<?php
// Uso: php scripts/install.php [--seed]
// Cria as tabelas; com --seed, preenche um banco vazio com os dados de demonstração.

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    exit;
}

require dirname(__DIR__) . '/lib/core.php';
require dirname(__DIR__) . '/lib/directory.php';
require dirname(__DIR__) . '/lib/setup.php';

date_default_timezone_set(config()['timezone'] ?? 'America/Sao_Paulo');
install_schema();
echo "Tabelas prontas.\n";

if (in_array('--seed', $argv, true)) {
    if (!database_is_empty()) {
        echo "O banco já tem dados; nada foi semeado.\n";
        exit;
    }
    transaction('seed_demo');
    echo "Dados de demonstração criados.\n";
}
