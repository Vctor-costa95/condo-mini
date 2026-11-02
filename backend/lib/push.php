<?php
// Notificações push pelo Firebase Cloud Messaging (API HTTP v1). O envio roda
// depois da resposta ao app (after_response) e não depende de bibliotecas: o
// token OAuth é um JWT assinado com a chave da conta de serviço.

declare(strict_types=1);

const FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

/**
 * config.php → 'push' => [
 *   'service_account_file' => caminho do JSON da conta de serviço (fora do public_html),
 *   'log_file' => opcional: grava as notificações em vez de enviar (testes locais),
 * ]
 */
function push_config(): array
{
    return config()['push'] ?? [];
}

// ---------------------------------------------------------------- destinatários

/** Quem mora no apartamento (moradores e síndico morador). @return list<string> */
function users_of_unit(string $condoId, string $unit): array
{
    return array_column(rows(
        'SELECT DISTINCT u.id FROM users u JOIN user_units uu ON uu.user_id = u.id WHERE u.condo_id = ? AND uu.unit = ?',
        [$condoId, $unit]
    ), 'id');
}

/** @return list<string> */
function managers_of(string $condoId): array
{
    return array_column(rows("SELECT id FROM users WHERE condo_id = ? AND role = 'MANAGER'", [$condoId]), 'id');
}

/** Todo mundo do condomínio: moradores, síndico e portaria. @return list<string> */
function everyone_in(string $condoId): array
{
    return array_column(rows('SELECT id FROM users WHERE condo_id = ?', [$condoId]), 'id');
}

// ---------------------------------------------------------------- envio

/**
 * Agenda a notificação para os aparelhos de [userIds], sem quem fez a ação.
 * [data] chega ao app junto (ex.: "tab" para abrir a aba certa).
 */
function notify(array $userIds, string $title, string $body, array $data = []): void
{
    $me = $GLOBALS['condomini_current_user_id'] ?? null;
    $userIds = array_values(array_unique(array_filter($userIds, fn ($id) => $id !== $me)));
    if ($userIds === [] || push_config() === []) {
        return;
    }
    after_response(function () use ($userIds, $title, $body, $data) {
        $placeholders = implode(',', array_fill(0, count($userIds), '?'));
        $tokens = array_column(rows("SELECT token FROM devices WHERE user_id IN ($placeholders)", $userIds), 'token');
        foreach ($tokens as $token) {
            send_push($token, $title, mb_substr($body, 0, 180), $data);
        }
    });
}

function send_push(string $token, string $title, string $body, array $data): void
{
    $config = push_config();
    $message = [
        'token' => $token,
        'notification' => ['title' => $title, 'body' => $body],
        'data' => array_map('strval', $data),
        'android' => ['priority' => 'HIGH', 'notification' => ['channel_id' => 'condomini']],
    ];
    if (!empty($config['log_file'])) {
        file_put_contents($config['log_file'], json_encode($message, JSON_UNESCAPED_UNICODE) . "\n", FILE_APPEND);
        return;
    }
    $account = service_account();
    $accessToken = fcm_access_token($account);
    if ($accessToken === null) {
        return;
    }
    [$status, $response] = http_post_json(
        "https://fcm.googleapis.com/v1/projects/{$account['project_id']}/messages:send",
        ['message' => $message],
        ["Authorization: Bearer $accessToken"]
    );
    // Aparelho desinstalou o app ou trocou de token: para de enviar para ele.
    if ($status === 404 || ($status === 400 && str_contains($response, 'registration token'))) {
        exec_sql('DELETE FROM devices WHERE token = ?', [$token]);
    } elseif ($status >= 400) {
        error_log("[condomini] FCM $status: $response");
    }
}

function service_account(): array
{
    static $account = null;
    if ($account === null) {
        $path = push_config()['service_account_file'] ?? '';
        $account = is_file($path) ? (json_decode((string) file_get_contents($path), true) ?: []) : [];
        if (!isset($account['client_email'], $account['private_key'], $account['project_id'])) {
            throw new RuntimeException('Conta de serviço do Firebase inválida ou ausente.');
        }
    }
    return $account;
}

/** Token OAuth de 1 hora, guardado em disco entre requisições. */
function fcm_access_token(array $account): ?string
{
    $cache = rtrim(config()['storage_dir'], '/\\') . '/fcm-token.json';
    $cached = is_file($cache) ? json_decode((string) file_get_contents($cache), true) : null;
    if (is_array($cached) && ($cached['expires_at'] ?? 0) > time() + 60) {
        return $cached['access_token'];
    }

    $now = time();
    $b64 = fn (string $raw) => rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
    $unsigned = $b64(json_encode(['alg' => 'RS256', 'typ' => 'JWT'])) . '.' . $b64(json_encode([
        'iss' => $account['client_email'],
        'scope' => FCM_SCOPE,
        'aud' => $account['token_uri'] ?? 'https://oauth2.googleapis.com/token',
        'iat' => $now,
        'exp' => $now + 3600,
    ]));
    if (!openssl_sign($unsigned, $signature, $account['private_key'], OPENSSL_ALGO_SHA256)) {
        throw new RuntimeException('Não foi possível assinar o token do Firebase.');
    }
    [$status, $response] = http_post_form($account['token_uri'] ?? 'https://oauth2.googleapis.com/token', [
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion' => $unsigned . '.' . $b64($signature),
    ]);
    $body = json_decode($response, true);
    if ($status !== 200 || !isset($body['access_token'])) {
        error_log("[condomini] OAuth do Firebase falhou ($status): $response");
        return null;
    }
    @file_put_contents($cache, json_encode([
        'access_token' => $body['access_token'],
        'expires_at' => $now + (int) ($body['expires_in'] ?? 3600),
    ]));
    return $body['access_token'];
}

/** @return array{int, string} */
function http_post_json(string $url, array $payload, array $headers = []): array
{
    return http_post($url, json_encode($payload, JSON_UNESCAPED_UNICODE), ['Content-Type: application/json', ...$headers]);
}

/** @return array{int, string} */
function http_post_form(string $url, array $fields): array
{
    return http_post($url, http_build_query($fields), ['Content-Type: application/x-www-form-urlencoded']);
}

/** @return array{int, string} */
function http_post(string $url, string $body, array $headers): array
{
    $context = stream_context_create(['http' => [
        'method' => 'POST',
        'header' => implode("\r\n", $headers),
        'content' => $body,
        'ignore_errors' => true,
        'timeout' => 10,
    ]]);
    $response = @file_get_contents($url, false, $context);
    preg_match('#^HTTP/\S+ (\d+)#', $http_response_header[0] ?? '', $m);
    return [(int) ($m[1] ?? 0), (string) $response];
}

// ---------------------------------------------------------------- textos

const PERIOD_LABELS = ['MORNING' => 'manhã', 'AFTERNOON' => 'tarde', 'NIGHT' => 'noite'];
const MONTHS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

/** "12/11 · tarde" */
function slot_label(string $date, string $period): string
{
    return date('d/m', strtotime($date)) . ' · ' . (PERIOD_LABELS[$period] ?? $period);
}

/** "2026-11" → "novembro de 2026" */
function month_label(string $reference): string
{
    [$year, $month] = explode('-', $reference);
    return MONTHS[(int) $month - 1] . " de $year";
}

function service_label(array $booking): string
{
    return $booking['type'] === 'MOVE' ? 'Mudança' : 'Reparo';
}

// ---------------------------------------------------------------- rotas

function register_push_routes(Router $r): void
{
    // O app chama ao entrar e quando o Firebase troca o token do aparelho.
    $r->add('POST', '/devices', function () {
        $me = current_user();
        $token = required_string('token', 512);
        if (!preg_match('/^[A-Za-z0-9_:\-.]+$/', $token)) {
            throw new ApiError(422, 'Token de notificação inválido.');
        }
        exec_sql(
            'INSERT INTO devices (token, user_id, session_hash, platform, updated_at) VALUES (?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), session_hash = VALUES(session_hash),
               platform = VALUES(platform), updated_at = VALUES(updated_at)',
            [$token, $me['id'], current_token_hash(), optional_string('platform', 20) ?: 'android', now_sql()]
        );
        send_json(['ok' => true]);
    });
}
