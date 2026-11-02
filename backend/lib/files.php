<?php
// Fotos e PDFs: gravados em storage/ e servidos só para quem é do mesmo condomínio.

declare(strict_types=1);

const IMAGE_TYPES = ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp', 'image/heic' => 'heic'];
const PDF_TYPES = ['application/pdf' => 'pdf'];

/** Endereço público da API, para montar as URLs das fotos. */
function base_url(): string
{
    $https = ($_SERVER['HTTPS'] ?? '') === 'on' || ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https';
    $scheme = $https ? 'https' : 'http';
    $dir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
    return $scheme . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . $dir;
}

function file_url(string $fileId): string
{
    return base_url() . '/files/' . $fileId;
}

/** @return list<string> */
function file_urls(?string $jsonIds): array
{
    $ids = $jsonIds === null ? [] : (json_decode($jsonIds, true) ?: []);
    return array_map('file_url', $ids);
}

/**
 * Aceita tanto o id quanto a URL devolvida por POST /files e confirma que o
 * arquivo é do condomínio. @return list<string> ids
 */
function own_file_ids(array $urlsOrIds, string $condoId): array
{
    $ids = [];
    foreach (string_list($urlsOrIds) as $value) {
        $id = basename(parse_url($value, PHP_URL_PATH) ?: $value);
        if (row('SELECT id FROM files WHERE id = ? AND condo_id = ?', [$id, $condoId]) === null) {
            throw new ApiError(422, 'Foto não encontrada; envie de novo.');
        }
        $ids[] = $id;
    }
    return array_slice($ids, 0, 3);
}

/** Grava o upload do campo [field] e devolve a linha de files. */
function store_upload(string $condoId, array $allowedTypes, string $field = 'file'): array
{
    $upload = $_FILES[$field] ?? null;
    if (!is_array($upload) || ($upload['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        $tooBig = in_array($upload['error'] ?? null, [UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE], true);
        throw new ApiError($tooBig ? 413 : 422, $tooBig ? 'Arquivo grande demais.' : 'Nenhum arquivo enviado.');
    }
    if ($upload['size'] > config()['max_upload_bytes']) {
        throw new ApiError(413, 'Arquivo grande demais.');
    }
    $mime = (new finfo(FILEINFO_MIME_TYPE))->file($upload['tmp_name']) ?: '';
    if (!isset($allowedTypes[$mime])) {
        throw new ApiError(415, 'Tipo de arquivo não aceito.');
    }
    $id = uuid();
    $storedName = $id . '.' . $allowedTypes[$mime];
    $dir = storage_dir($condoId);
    if (!move_uploaded_file($upload['tmp_name'], "$dir/$storedName")) {
        throw new ApiError(500, 'Não foi possível salvar o arquivo.');
    }
    $file = [
        'id' => $id,
        'condo_id' => $condoId,
        'stored_name' => $storedName,
        'original_name' => mb_substr(basename((string) $upload['name']), 0, 255) ?: $storedName,
        'mime' => $mime,
        'size_bytes' => (int) $upload['size'],
        'created_by' => current_user()['id'],
        'created_at' => now_sql(),
    ];
    exec_sql(
        'INSERT INTO files (id, condo_id, stored_name, original_name, mime, size_bytes, created_by, created_at)
         VALUES (:id, :condo_id, :stored_name, :original_name, :mime, :size_bytes, :created_by, :created_at)',
        $file
    );
    return $file;
}

function storage_dir(string $condoId): string
{
    $dir = rtrim(config()['storage_dir'], '/\\') . '/' . preg_replace('/[^A-Za-z0-9_-]/', '', $condoId);
    if (!is_dir($dir) && !mkdir($dir, 0750, true) && !is_dir($dir)) {
        throw new ApiError(500, 'Pasta de arquivos indisponível.');
    }
    return $dir;
}

function file_path(array $file): string
{
    return storage_dir($file['condo_id']) . '/' . $file['stored_name'];
}

function delete_file(?string $fileId): void
{
    if ($fileId === null) {
        return;
    }
    $file = row('SELECT * FROM files WHERE id = ?', [$fileId]);
    if ($file !== null) {
        @unlink(file_path($file));
        exec_sql('DELETE FROM files WHERE id = ?', [$fileId]);
    }
}

function register_file_routes(Router $r): void
{
    // Foto avulsa (ocorrência, encomenda); o app manda a URL devolvida junto do registro.
    $r->add('POST', '/files', function () {
        $file = store_upload(condo_id(), IMAGE_TYPES);
        send_json(['id' => $file['id'], 'url' => file_url($file['id'])], 201);
    });

    $r->add('GET', '/files/{id}', function (string $id) {
        $file = require_same_condo(row('SELECT * FROM files WHERE id = ?', [$id]), 'Arquivo não encontrado.');
        // Boleto e foto de encomenda são só do apartamento de destino.
        if (current_user()['role'] === 'RESIDENT') {
            $owner = row(
                'SELECT unit FROM unit_documents WHERE file_id = ? UNION SELECT unit FROM parcels WHERE photo_file_id = ?',
                [$id, $id]
            );
            if ($owner !== null && !in_array($owner['unit'], user_units(current_user()['id']), true)) {
                throw new ApiError(404, 'Arquivo não encontrado.');
            }
        }
        send_file(file_path($file), $file['mime'], $file['original_name']);
    });
}
