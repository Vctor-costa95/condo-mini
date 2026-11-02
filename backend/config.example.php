<?php
// Copie para config.php (fora do git) e preencha. Na Hostinger, o app fala com o
// MySQL em 127.0.0.1:3306; o host srvNNNN.hstgr.io é só para acesso externo.
return [
    'db' => [
        'host' => '127.0.0.1',
        'port' => 3306,
        'name' => 'u000000000_condomini',
        'user' => 'u000000000_condomini',
        'pass' => '',
    ],
    // Pasta dos arquivos enviados (fotos e PDFs). Precisa ser gravável pelo PHP.
    'storage_dir' => __DIR__ . '/storage',
    // Contas de teste sem senha (morador, sindico, porteiro, admin).
    // Desligue antes de cadastrar moradores de verdade.
    'allow_passwordless_logins' => false,
    'timezone' => 'America/Sao_Paulo',
    'max_upload_bytes' => 15 * 1024 * 1024,
    // Notificações push (Firebase Cloud Messaging). Sem esta chave, nada é enviado.
    // service_account_file: JSON da conta de serviço (Firebase → Configurações do
    // projeto → Contas de serviço → Gerar nova chave privada), FORA do public_html.
    // Para testar localmente sem Firebase, troque por 'log_file' => caminho de um .log.
    // 'push' => ['service_account_file' => '/home/u000000000/condomini-firebase.json'],
];
