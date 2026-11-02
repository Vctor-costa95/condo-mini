<?php
// Ponto de entrada da API do Condo-mini: toda requisição passa por aqui (.htaccess).

declare(strict_types=1);

require __DIR__ . '/lib/core.php';
require __DIR__ . '/lib/files.php';
require __DIR__ . '/lib/directory.php';
require __DIR__ . '/lib/community.php';
require __DIR__ . '/lib/records.php';
require __DIR__ . '/lib/push.php';

try {
    date_default_timezone_set(config()['timezone'] ?? 'America/Sao_Paulo');
    db()->exec("SET time_zone = '" . date('P') . "'");

    $router = new Router();
    $router->add('GET', '/health', fn () => send_json(['ok' => true, 'time' => iso(now_sql())]));
    register_directory_routes($router);
    register_community_routes($router);
    register_record_routes($router);
    register_file_routes($router);
    register_push_routes($router);

    // Caminho relativo à pasta da API, sem query string; aceita o prefixo /v1.
    $base = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'])), '/');
    $path = substr(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) ?: '/', strlen($base)) ?: '/';
    $path = preg_replace('#^/v1(?=/|$)#', '', $path) ?: '/';
    $router->dispatch($_SERVER['REQUEST_METHOD'], rtrim($path, '/') ?: '/');
} catch (ApiError $e) {
    send_json(['error' => $e->getMessage()], $e->status);
} catch (Throwable $e) {
    error_log('[condomini] ' . $e);
    send_json(['error' => 'Erro no servidor. Tente de novo em instantes.'], 500);
}
