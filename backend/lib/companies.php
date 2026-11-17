<?php
// Administradoras: a plataforma (perfil PLATFORM, a equipe Mini Condo) cadastra,
// aloca condomínios e sobe a logo, que o app e o painel mostram no lugar da nossa.

declare(strict_types=1);

const LOGO_TYPES = ['image/png' => 'png', 'image/webp' => 'webp', 'image/jpeg' => 'jpg'];
const LOGO_MAX_BYTES = 1024 * 1024;
const LOGO_MIN_SIDE = 128;

/** Pasta das logos, separada das pastas de cada condomínio. */
function logo_dir(): string
{
    return storage_dir('_companies');
}

function logo_url(array $company): ?string
{
    if ($company['logo_file'] === null) {
        return null;
    }
    // ?v= muda a cada troca: o app e o navegador podem guardar a imagem sem medo.
    return base_url() . '/companies/' . $company['id'] . '/logo?v=' . strtotime((string) $company['logo_updated_at']);
}

/**
 * Marca que o app e o painel mostram. Sem administradora (plataforma) ou sem logo,
 * logoUrl vem null e cada um mostra a marca do Mini Condo.
 */
function brand_json(?string $companyId): array
{
    $company = $companyId === null ? null : row('SELECT * FROM companies WHERE id = ?', [$companyId]);
    if ($company === null) {
        return ['companyId' => null, 'name' => 'Mini Condo', 'logoUrl' => null, 'color' => null];
    }
    return [
        'companyId' => $company['id'],
        'name' => $company['name'],
        'logoUrl' => logo_url($company),
        'color' => $company['brand_color'] !== '' ? $company['brand_color'] : null,
    ];
}

function company_json(array $c): array
{
    return [
        'id' => $c['id'],
        'name' => $c['name'],
        'cnpj' => $c['cnpj'],
        'brandColor' => $c['brand_color'],
        'logoUrl' => logo_url($c),
        'active' => (bool) $c['active'],
        'condoCount' => (int) row('SELECT COUNT(*) AS n FROM condominiums WHERE company_id = ?', [$c['id']])['n'],
        'adminCount' => (int) row("SELECT COUNT(*) AS n FROM users WHERE company_id = ? AND role = 'ADMIN'", [$c['id']])['n'],
        // Pedidos de cadastro esperando em prédios dela sem síndico: sem administrador também, só a plataforma os vê.
        'unattendedSignups' => (int) row(
            "SELECT COUNT(*) AS n FROM signup_requests s JOIN condominiums c ON c.id = s.condo_id
             WHERE c.company_id = ? AND s.status = 'PENDING'
               AND NOT EXISTS (SELECT 1 FROM users m WHERE m.condo_id = c.id AND m.role = 'MANAGER')",
            [$c['id']]
        )['n'],
        'createdAt' => iso($c['created_at']),
    ];
}

function find_company(string $id): array
{
    return row('SELECT * FROM companies WHERE id = ?', [$id]) ?? throw new ApiError(404, 'Administradora não encontrada.');
}

function delete_logo_file(array $company): void
{
    if ($company['logo_file'] !== null) {
        @unlink(logo_dir() . '/' . basename($company['logo_file']));
    }
}

function register_company_routes(Router $r): void
{
    $r->add('GET', '/companies', function () {
        require_role('PLATFORM');
        send_json(array_map('company_json', rows('SELECT * FROM companies ORDER BY name')));
    });

    // Cria quando não há id; senão atualiza. active = false suspende o acesso de
    // todos os cadastros da administradora (administradores e condomínios dela).
    $r->add('POST', '/companies', function () {
        require_role('PLATFORM');
        $id = optional_string('id', 36);
        $existing = $id === '' ? null : find_company($id);
        $color = optional_string('brandColor', 7);
        if ($color !== '' && !preg_match('/^#[0-9A-Fa-f]{6}$/', $color)) {
            throw new ApiError(422, 'Cor no formato #RRGGBB.');
        }
        $fields = [
            'id' => $existing['id'] ?? uuid(),
            'name' => required_string('name', 120),
            'cnpj' => optional_string('cnpj', 20),
            'brand_color' => strtoupper($color),
            'active' => input('active', $existing === null ? true : (bool) $existing['active']) ? 1 : 0,
            'created_at' => $existing['created_at'] ?? now_sql(),
        ];
        exec_sql(
            'INSERT INTO companies (id, name, cnpj, brand_color, active, created_at)
             VALUES (:id, :name, :cnpj, :brand_color, :active, :created_at)
             ON DUPLICATE KEY UPDATE name = VALUES(name), cnpj = VALUES(cnpj),
               brand_color = VALUES(brand_color), active = VALUES(active)',
            $fields
        );
        send_json(company_json(find_company($fields['id'])), $existing === null ? 201 : 200);
    });

    // Só sai quando não cuida de nenhum condomínio; os administradores dela saem junto.
    $r->add('DELETE', '/companies/{id}', function (string $id) {
        require_role('PLATFORM');
        $company = find_company($id);
        if ((int) row('SELECT COUNT(*) AS n FROM condominiums WHERE company_id = ?', [$id])['n'] > 0) {
            throw new ApiError(409, 'Mova ou desaloque os condomínios desta administradora antes de excluí-la.');
        }
        exec_sql('DELETE FROM companies WHERE id = ?', [$id]);
        delete_logo_file($company);
        send_json(['ok' => true]);
    });

    // Logo: PNG, WebP ou JPEG de até 1 MB, com pelo menos 128 px de cada lado.
    // SVG fica de fora (pode carregar script e o Android não lê direto).
    $r->add('POST', '/companies/{id}/logo', function (string $id) {
        require_role('PLATFORM');
        $company = find_company($id);
        $upload = $_FILES['file'] ?? null;
        if (!is_array($upload) || ($upload['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
            $tooBig = in_array($upload['error'] ?? null, [UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE], true);
            throw new ApiError($tooBig ? 413 : 422, $tooBig ? 'Logo grande demais (até 1 MB).' : 'Nenhum arquivo enviado.');
        }
        if ($upload['size'] > LOGO_MAX_BYTES) {
            throw new ApiError(413, 'Logo grande demais (até 1 MB).');
        }
        $mime = (new finfo(FILEINFO_MIME_TYPE))->file($upload['tmp_name']) ?: '';
        if (!isset(LOGO_TYPES[$mime])) {
            throw new ApiError(415, 'Envie a logo em PNG, WebP ou JPEG.');
        }
        $size = @getimagesize($upload['tmp_name']);
        if ($size === false) {
            throw new ApiError(415, 'Imagem inválida.');
        }
        if ($size[0] < LOGO_MIN_SIDE || $size[1] < LOGO_MIN_SIDE) {
            throw new ApiError(422, 'A logo precisa ter pelo menos ' . LOGO_MIN_SIDE . ' px de largura e de altura.');
        }
        $storedName = $id . '-' . bin2hex(random_bytes(4)) . '.' . LOGO_TYPES[$mime];
        if (!move_uploaded_file($upload['tmp_name'], logo_dir() . '/' . $storedName)) {
            throw new ApiError(500, 'Não foi possível salvar a logo.');
        }
        delete_logo_file($company);
        exec_sql('UPDATE companies SET logo_file = ?, logo_updated_at = ? WHERE id = ?', [$storedName, now_sql(), $id]);
        send_json(company_json(find_company($id)));
    });

    $r->add('DELETE', '/companies/{id}/logo', function (string $id) {
        require_role('PLATFORM');
        $company = find_company($id);
        delete_logo_file($company);
        exec_sql('UPDATE companies SET logo_file = NULL, logo_updated_at = NULL WHERE id = ?', [$id]);
        send_json(company_json(find_company($id)));
    });

    // Pública: a logo não é segredo, e assim cabe num <img> do painel e na tela de
    // abertura do app antes do login. O endereço só existe para quem recebeu o id.
    $r->add('GET', '/companies/{id}/logo', function (string $id) {
        $company = row('SELECT * FROM companies WHERE id = ?', [$id]);
        $path = $company === null || $company['logo_file'] === null ? null : logo_dir() . '/' . basename($company['logo_file']);
        if ($path === null || !is_file($path)) {
            throw new ApiError(404, 'Logo não encontrada.');
        }
        $ext = pathinfo($path, PATHINFO_EXTENSION);
        header('Content-Type: ' . (array_search($ext, LOGO_TYPES, true) ?: 'application/octet-stream'));
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        header('Cache-Control: public, max-age=31536000, immutable');
        readfile($path);
        exit;
    });

    // Aloca o condomínio numa administradora (companyId vazio = desaloca).
    // Os administradores da antiga deixam de vê-lo na hora.
    $r->add('PUT', '/condos/{id}/company', function (string $id) {
        require_role('PLATFORM');
        if (row('SELECT id FROM condominiums WHERE id = ?', [$id]) === null) {
            throw new ApiError(404, 'Condomínio não encontrado.');
        }
        $companyId = optional_string('companyId', 36);
        exec_sql('UPDATE condominiums SET company_id = ? WHERE id = ?', [
            $companyId === '' ? null : require_company($companyId), $id,
        ]);
        send_json(condo_json(row('SELECT * FROM condominiums WHERE id = ?', [$id])));
    });
}
