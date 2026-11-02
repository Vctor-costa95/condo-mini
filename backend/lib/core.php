<?php
// Infraestrutura da API: configuração, banco, respostas JSON, roteador e sessão.

declare(strict_types=1);

/** Erro que vira resposta JSON {"error": "..."} com o status HTTP dado. */
final class ApiError extends RuntimeException
{
    public function __construct(public readonly int $status, string $message)
    {
        parent::__construct($message);
    }
}

function config(): array
{
    static $config = null;
    if ($config === null) {
        $path = dirname(__DIR__) . '/config.php';
        if (!is_file($path)) {
            throw new ApiError(500, 'Servidor sem config.php.');
        }
        $config = require $path;
    }
    return $config;
}

function db(): PDO
{
    static $pdo = null;
    if ($pdo === null) {
        $c = config()['db'];
        $pdo = new PDO(
            "mysql:host={$c['host']};port={$c['port']};dbname={$c['name']};charset=utf8mb4",
            $c['user'],
            $c['pass'],
            [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES => false,
            ]
        );
    }
    return $pdo;
}

/** @return list<array<string, mixed>> */
function rows(string $sql, array $params = []): array
{
    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    return $stmt->fetchAll();
}

function row(string $sql, array $params = []): ?array
{
    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    $found = $stmt->fetch();
    return $found === false ? null : $found;
}

function exec_sql(string $sql, array $params = []): int
{
    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    return $stmt->rowCount();
}

function transaction(callable $work): mixed
{
    db()->beginTransaction();
    try {
        $result = $work();
        db()->commit();
        return $result;
    } catch (Throwable $e) {
        db()->rollBack();
        throw $e;
    }
}

function uuid(): string
{
    $b = random_bytes(16);
    $b[6] = chr((ord($b[6]) & 0x0f) | 0x40);
    $b[8] = chr((ord($b[8]) & 0x3f) | 0x80);
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($b), 4));
}

function now_sql(): string
{
    return date('Y-m-d H:i:s');
}

function today(): string
{
    return date('Y-m-d');
}

/** "2026-11-02 10:30:00" → "2026-11-02T10:30:00", como o LocalDateTime do app lê. */
function iso(?string $sqlDateTime): ?string
{
    return $sqlDateTime === null ? null : str_replace(' ', 'T', $sqlDateTime);
}

// ---------------------------------------------------------------- requisição

function json_body(): array
{
    static $body = null;
    if ($body === null) {
        $raw = file_get_contents('php://input') ?: '';
        $body = $raw === '' ? [] : json_decode($raw, true);
        if (!is_array($body)) {
            throw new ApiError(400, 'JSON inválido.');
        }
    }
    return $body;
}

/** Campo do corpo JSON ou do formulário multipart. */
function input(string $key, mixed $default = null): mixed
{
    if (str_starts_with($_SERVER['CONTENT_TYPE'] ?? '', 'multipart/form-data')) {
        return $_POST[$key] ?? $default;
    }
    return json_body()[$key] ?? $default;
}

function required_string(string $key, int $maxLength = 255): string
{
    $value = trim((string) input($key, ''));
    if ($value === '') {
        throw new ApiError(422, "Campo obrigatório: $key.");
    }
    return mb_substr($value, 0, $maxLength);
}

function optional_string(string $key, int $maxLength = 255): string
{
    return mb_substr(trim((string) input($key, '')), 0, $maxLength);
}

function query(string $key, ?string $default = null): ?string
{
    $value = $_GET[$key] ?? $default;
    return is_string($value) ? trim($value) : $default;
}

function required_query(string $key): string
{
    $value = query($key, '');
    if ($value === '') {
        throw new ApiError(422, "Parâmetro obrigatório: $key.");
    }
    return $value;
}

function require_enum(string $value, array $allowed, string $field): string
{
    if (!in_array($value, $allowed, true)) {
        throw new ApiError(422, "Valor inválido para $field.");
    }
    return $value;
}

function require_date(string $value, string $field = 'date'): string
{
    $d = DateTimeImmutable::createFromFormat('!Y-m-d', $value);
    if ($d === false || $d->format('Y-m-d') !== $value) {
        throw new ApiError(422, "Data inválida em $field.");
    }
    return $value;
}

/** "2026-11" */
function require_month(string $value): string
{
    if (!preg_match('/^\d{4}-(0[1-9]|1[0-2])$/', $value)) {
        throw new ApiError(422, 'Mês de referência inválido.');
    }
    return $value;
}

/** @return list<string> */
function string_list(mixed $value): array
{
    if (!is_array($value)) {
        return [];
    }
    return array_values(array_filter(array_map(fn ($v) => trim((string) $v), $value), fn ($v) => $v !== ''));
}

// ---------------------------------------------------------------- respostas

function send_json(mixed $data, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION);
    run_after_response();
    exit;
}

/** Trabalho que não precisa segurar a resposta (envio de notificações). */
function after_response(callable $work): void
{
    $GLOBALS['condomini_after_response'][] = $work;
}

function run_after_response(): void
{
    $jobs = $GLOBALS['condomini_after_response'] ?? [];
    if ($jobs === []) {
        return;
    }
    $GLOBALS['condomini_after_response'] = [];
    // Entrega a resposta ao app antes; o resto roda com a conexão já fechada.
    if (function_exists('litespeed_finish_request')) {
        litespeed_finish_request();
    } elseif (function_exists('fastcgi_finish_request')) {
        fastcgi_finish_request();
    }
    ignore_user_abort(true);
    foreach ($jobs as $job) {
        try {
            $job();
        } catch (Throwable $e) {
            error_log('[condomini] ' . $e);
        }
    }
}

function send_file(string $path, string $mime, string $downloadName): never
{
    if (!is_file($path)) {
        throw new ApiError(404, 'Arquivo não encontrado.');
    }
    header('Content-Type: ' . $mime);
    header('Content-Length: ' . filesize($path));
    header('Content-Disposition: inline; filename="' . str_replace('"', '', $downloadName) . '"');
    header('Cache-Control: private, max-age=86400');
    readfile($path);
    exit;
}

// ---------------------------------------------------------------- roteador

final class Router
{
    /** @var list<array{string, string, callable}> */
    private array $routes = [];

    public function add(string $method, string $pattern, callable $handler): void
    {
        $regex = '#^' . preg_replace('#\{(\w+)\}#', '(?P<$1>[^/]+)', $pattern) . '$#';
        $this->routes[] = [$method, $regex, $handler];
    }

    public function dispatch(string $method, string $path): mixed
    {
        $pathMatched = false;
        foreach ($this->routes as [$routeMethod, $regex, $handler]) {
            if (!preg_match($regex, $path, $m)) {
                continue;
            }
            $pathMatched = true;
            if ($routeMethod !== $method) {
                continue;
            }
            $params = array_map('urldecode', array_filter($m, 'is_string', ARRAY_FILTER_USE_KEY));
            return $handler(...$params);
        }
        throw new ApiError($pathMatched ? 405 : 404, $pathMatched ? 'Método não permitido.' : 'Rota não encontrada.');
    }
}

// ---------------------------------------------------------------- sessão

/** Usuário do token Bearer; 401 se ausente ou inválido. */
function current_user(): array
{
    static $user = null;
    if ($user !== null) {
        return $user;
    }
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer\s+([A-Za-z0-9_-]{20,})$/', $header, $m)) {
        throw new ApiError(401, 'Faça login novamente.');
    }
    $hash = hash('sha256', $m[1]);
    $found = row(
        'SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?',
        [$hash]
    );
    if ($found === null) {
        throw new ApiError(401, 'Faça login novamente.');
    }
    exec_sql('UPDATE sessions SET last_used_at = ? WHERE token_hash = ?', [now_sql(), $hash]);
    $user = $found;
    // Quem fez a ação não recebe a própria notificação (ver notify()).
    $GLOBALS['condomini_current_user_id'] = $user['id'];
    return $user;
}

function current_token_hash(): ?string
{
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    return preg_match('/^Bearer\s+(\S+)$/', $header, $m) ? hash('sha256', $m[1]) : null;
}

function require_role(string ...$roles): array
{
    $user = current_user();
    if (!in_array($user['role'], $roles, true)) {
        throw new ApiError(403, 'Seu perfil não tem acesso a esta ação.');
    }
    return $user;
}

/**
 * Condomínio da requisição: o do cadastro, ou o informado em ?condoId= / corpo
 * para o administrador, que atende vários.
 */
function condo_id(?string $requested = null): string
{
    $user = current_user();
    $requested ??= query('condoId') ?? (string) input('condoId', '');
    if ($user['role'] === 'ADMIN') {
        if ($requested === '' || row('SELECT id FROM condominiums WHERE id = ?', [$requested]) === null) {
            throw new ApiError(422, 'Escolha um condomínio.');
        }
        return $requested;
    }
    if ($requested !== '' && $requested !== $user['condo_id']) {
        throw new ApiError(403, 'Condomínio de outro cadastro.');
    }
    return (string) $user['condo_id'];
}

/** Garante que a linha pertence ao condomínio de quem pede. */
function require_same_condo(?array $record, string $notFound = 'Registro não encontrado.'): array
{
    if ($record === null) {
        throw new ApiError(404, $notFound);
    }
    $user = current_user();
    if ($user['role'] !== 'ADMIN' && $record['condo_id'] !== $user['condo_id']) {
        throw new ApiError(404, $notFound);
    }
    return $record;
}

/** @return list<string> */
function user_units(string $userId): array
{
    return array_column(rows('SELECT unit FROM user_units WHERE user_id = ? ORDER BY position, unit', [$userId]), 'unit');
}

/** Morador só mexe nos próprios apartamentos; a administração, em qualquer um. */
function require_unit_access(string $unit): string
{
    $user = current_user();
    if ($user['role'] === 'RESIDENT' && !in_array($unit, user_units($user['id']), true)) {
        throw new ApiError(403, 'Apartamento de outro cadastro.');
    }
    return $unit;
}

/** Como o nome aparece para os moradores: "Síndico - Carlos Andrade", "Porteiro José". */
function author_label(array $user): string
{
    return match ($user['role']) {
        'MANAGER' => 'Síndico - ' . $user['name'],
        'DOORMAN' => 'Porteiro ' . explode(' ', trim($user['name']))[0],
        'ADMIN' => 'Administração',
        default => $user['name'],
    };
}

/** Minúsculas e sem acentos: "José.Silva" e "jose.silva" são o mesmo usuário. */
function login_key(string $login): string
{
    $lower = mb_strtolower(trim($login));
    if (class_exists('Normalizer')) {
        $lower = preg_replace('/\p{Mn}/u', '', Normalizer::normalize($lower, Normalizer::FORM_D));
    } else {
        $lower = strtr($lower, ['á' => 'a', 'à' => 'a', 'â' => 'a', 'ã' => 'a', 'é' => 'e', 'ê' => 'e',
            'í' => 'i', 'ó' => 'o', 'ô' => 'o', 'õ' => 'o', 'ú' => 'u', 'ü' => 'u', 'ç' => 'c']);
    }
    return $lower;
}
