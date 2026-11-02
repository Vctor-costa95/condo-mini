<?php
// Login, cadastro de pessoas e condomínios (com blocos e apartamentos).

declare(strict_types=1);

function user_json(array $u): array
{
    $units = user_units($u['id']);
    return [
        'id' => $u['id'],
        'name' => $u['name'],
        'unit' => $units[0] ?? '',
        'units' => $units,
        'role' => $u['role'],
        'email' => $u['email'],
        'phone' => $u['phone'],
        'condoId' => $u['condo_id'] ?? '',
        'login' => $u['login'],
    ];
}

function condo_json(array $c): array
{
    $towers = rows('SELECT * FROM towers WHERE condo_id = ? ORDER BY position, name', [$c['id']]);
    return [
        'id' => $c['id'],
        'name' => $c['name'],
        'address' => $c['address'],
        'towers' => array_map(fn ($t) => [
            'id' => $t['id'],
            'name' => $t['name'],
            'floors' => (int) $t['floors'],
            'unitsPerFloor' => (int) $t['units_per_floor'],
            'floorOverrides' => (object) (json_decode($t['floor_overrides'], true) ?: []),
        ], $towers),
    ];
}

/**
 * Rótulos dos apartamentos, com a mesma numeração do app (Tower.units()):
 * andar + posição com dois dígitos; no térreo, "T" + posição.
 * @return list<string>
 */
function tower_unit_labels(array $tower): array
{
    $overrides = $tower['floorOverrides'];
    $labels = [];
    for ($floor = 0; $floor <= $tower['floors']; $floor++) {
        $count = $overrides[(string) $floor] ?? ($floor === 0 ? 0 : $tower['unitsPerFloor']);
        for ($p = 1; $p <= $count; $p++) {
            $suffix = str_pad((string) $p, 2, '0', STR_PAD_LEFT);
            $number = $floor === 0 ? "T$suffix" : "$floor$suffix";
            $labels[] = "Apto $number - {$tower['name']}";
        }
    }
    return $labels;
}

/** Troca a lista de apartamentos do cadastro, na ordem dada. */
function set_user_units(string $userId, array $units): void
{
    exec_sql('DELETE FROM user_units WHERE user_id = ?', [$userId]);
    foreach (array_values(array_unique(string_list($units))) as $i => $unit) {
        exec_sql('INSERT INTO user_units (user_id, unit, position) VALUES (?, ?, ?)', [$userId, mb_substr($unit, 0, 80), $i]);
    }
}

function login_available(string $login, string $exceptUserId = ''): bool
{
    return row('SELECT id FROM users WHERE login_key = ? AND id <> ?', [login_key($login), $exceptUserId]) === null;
}

/** Usuário que o administrador pode editar: qualquer um; o síndico, só os do condomínio. */
function editable_user(string $id): array
{
    $user = row('SELECT * FROM users WHERE id = ?', [$id]);
    $me = current_user();
    if ($user === null || ($me['role'] !== 'ADMIN' && $user['condo_id'] !== $me['condo_id'])) {
        throw new ApiError(404, 'Cadastro não encontrado.');
    }
    return $user;
}

function register_directory_routes(Router $r): void
{
    $r->add('POST', '/auth/login', function () {
        $login = trim((string) input('login', ''));
        $password = (string) input('password', '');
        $user = $login === '' ? null : row('SELECT * FROM users WHERE login_key = ?', [login_key($login)]);
        $ok = $user !== null && (
            $user['password_hash'] === null
                ? config()['allow_passwordless_logins'] && $password === ''
                : password_verify($password, $user['password_hash'])
        );
        if (!$ok) {
            throw new ApiError(401, 'Usuário ou senha incorretos.');
        }
        $token = rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
        exec_sql(
            'INSERT INTO sessions (token_hash, user_id, created_at, last_used_at) VALUES (?, ?, ?, ?)',
            [hash('sha256', $token), $user['id'], now_sql(), now_sql()]
        );
        send_json(['token' => $token, 'user' => user_json($user)]);
    });

    $r->add('POST', '/auth/logout', function () {
        if (($hash = current_token_hash()) !== null) {
            exec_sql('DELETE FROM sessions WHERE token_hash = ?', [$hash]);
        }
        send_json(['ok' => true]);
    });

    $r->add('GET', '/me', fn () => send_json(user_json(current_user())));

    $r->add('PATCH', '/me', function () {
        $me = current_user();
        exec_sql('UPDATE users SET email = ?, phone = ? WHERE id = ?', [
            optional_string('email', 160), optional_string('phone', 40), $me['id'],
        ]);
        send_json(user_json(row('SELECT * FROM users WHERE id = ?', [$me['id']])));
    });

    $r->add('GET', '/condos', function () {
        $me = current_user();
        $list = $me['role'] === 'ADMIN'
            ? rows('SELECT * FROM condominiums ORDER BY name')
            : rows('SELECT * FROM condominiums WHERE id = ?', [$me['condo_id']]);
        send_json(array_map('condo_json', $list));
    });

    // Cria quando não há id; senão atualiza. Apartamentos que deixam de existir
    // saem do cadastro de quem morava neles.
    $r->add('POST', '/condos', function () {
        require_role('ADMIN');
        $id = optional_string('id', 36) ?: uuid();
        $towers = [];
        foreach ((array) input('towers', []) as $i => $t) {
            $name = trim((string) ($t['name'] ?? ''));
            if ($name === '') {
                throw new ApiError(422, 'Todo bloco precisa de nome.');
            }
            $overrides = [];
            foreach ((array) ($t['floorOverrides'] ?? []) as $floor => $count) {
                $overrides[(string) (int) $floor] = max(0, (int) $count);
            }
            $towers[] = [
                'id' => trim((string) ($t['id'] ?? '')) ?: uuid(),
                'name' => mb_substr($name, 0, 80),
                'floors' => max(0, min(200, (int) ($t['floors'] ?? 0))),
                'unitsPerFloor' => max(0, min(100, (int) ($t['unitsPerFloor'] ?? 0))),
                'floorOverrides' => $overrides,
                'position' => $i,
            ];
        }
        $name = required_string('name', 120);
        $address = optional_string('address');
        transaction(function () use ($id, $name, $address, $towers) {
            exec_sql(
                'INSERT INTO condominiums (id, name, address) VALUES (?, ?, ?)
                 ON DUPLICATE KEY UPDATE name = VALUES(name), address = VALUES(address)',
                [$id, $name, $address]
            );
            exec_sql('DELETE FROM towers WHERE condo_id = ?', [$id]);
            $labels = [];
            foreach ($towers as $t) {
                exec_sql(
                    'INSERT INTO towers (id, condo_id, name, floors, units_per_floor, floor_overrides, position)
                     VALUES (?, ?, ?, ?, ?, ?, ?)',
                    [$t['id'], $id, $t['name'], $t['floors'], $t['unitsPerFloor'], json_encode((object) $t['floorOverrides']), $t['position']]
                );
                array_push($labels, ...tower_unit_labels($t));
            }
            $valid = array_flip($labels);
            foreach (rows('SELECT uu.user_id, uu.unit FROM user_units uu JOIN users u ON u.id = uu.user_id WHERE u.condo_id = ?', [$id]) as $link) {
                if (!isset($valid[$link['unit']])) {
                    exec_sql('DELETE FROM user_units WHERE user_id = ? AND unit = ?', [$link['user_id'], $link['unit']]);
                }
            }
        });
        send_json(condo_json(row('SELECT * FROM condominiums WHERE id = ?', [$id])));
    });

    // Pessoas do condomínio: administração, síndico e portaria. O administrador
    // também vê os outros administradores.
    $r->add('GET', '/users', function () {
        $me = require_role('ADMIN', 'MANAGER', 'DOORMAN');
        $condoId = condo_id();
        $list = $me['role'] === 'ADMIN'
            ? rows("SELECT * FROM users WHERE condo_id = ? OR role = 'ADMIN' ORDER BY name", [$condoId])
            : rows('SELECT * FROM users WHERE condo_id = ? ORDER BY name', [$condoId]);
        send_json(array_map('user_json', $list));
    });

    $r->add('GET', '/logins/available', function () {
        require_role('ADMIN');
        send_json(['available' => login_available(query('login', ''), query('exceptUserId', ''))]);
    });

    // Cria quando não há id (no condomínio informado); senão atualiza.
    // Senha em branco na edição mantém a atual.
    $r->add('POST', '/users', function () {
        require_role('ADMIN');
        $id = optional_string('id', 36);
        $existing = $id === '' ? null : row('SELECT * FROM users WHERE id = ?', [$id]);
        $role = require_enum((string) input('role', ''), ['ADMIN', 'MANAGER', 'DOORMAN', 'RESIDENT'], 'role');
        $condoId = $role === 'ADMIN' ? null : condo_id($existing['condo_id'] ?? null);
        $login = required_string('login', 80);
        if (!login_available($login, $id)) {
            throw new ApiError(409, 'Este usuário de acesso já está em uso.');
        }
        $password = (string) input('password', '');
        if ($existing === null && $password === '') {
            throw new ApiError(422, 'Defina uma senha para o novo cadastro.');
        }
        if ($password !== '' && mb_strlen($password) < 6) {
            throw new ApiError(422, 'A senha precisa de pelo menos 6 caracteres.');
        }
        $id = $id ?: uuid();
        $fields = [
            'id' => $id,
            'condo_id' => $condoId,
            'name' => required_string('name', 120),
            'role' => $role,
            'email' => optional_string('email', 160),
            'phone' => optional_string('phone', 40),
            'login' => $login,
            'login_key' => login_key($login),
            'password_hash' => $password !== '' ? password_hash($password, PASSWORD_DEFAULT) : ($existing['password_hash'] ?? null),
        ];
        transaction(function () use ($fields, $role) {
            exec_sql(
                'INSERT INTO users (id, condo_id, name, role, email, phone, login, login_key, password_hash)
                 VALUES (:id, :condo_id, :name, :role, :email, :phone, :login, :login_key, :password_hash)
                 ON DUPLICATE KEY UPDATE condo_id = VALUES(condo_id), name = VALUES(name), role = VALUES(role),
                   email = VALUES(email), phone = VALUES(phone), login = VALUES(login),
                   login_key = VALUES(login_key), password_hash = VALUES(password_hash)',
                $fields
            );
            set_user_units($fields['id'], $role === 'ADMIN' ? [] : (array) input('units', []));
        });
        send_json(user_json(row('SELECT * FROM users WHERE id = ?', [$id])));
    });

    $r->add('DELETE', '/users/{id}', function (string $id) {
        require_role('ADMIN');
        editable_user($id);
        if ($id === current_user()['id']) {
            throw new ApiError(422, 'Você não pode excluir o próprio cadastro.');
        }
        exec_sql('DELETE FROM users WHERE id = ?', [$id]);
        send_json(['ok' => true]);
    });

    $r->add('POST', '/users/{id}/units', function (string $id) {
        require_role('ADMIN');
        editable_user($id);
        set_user_units($id, [...user_units($id), required_string('unit', 80)]);
        send_json(user_json(row('SELECT * FROM users WHERE id = ?', [$id])));
    });

    $r->add('DELETE', '/users/{id}/units', function (string $id) {
        require_role('ADMIN');
        editable_user($id);
        exec_sql('DELETE FROM user_units WHERE user_id = ? AND unit = ?', [$id, query('unit', '')]);
        send_json(user_json(row('SELECT * FROM users WHERE id = ?', [$id])));
    });
}
