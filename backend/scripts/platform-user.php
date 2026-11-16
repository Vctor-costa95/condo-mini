<?php
// Uso: php scripts/platform-user.php <usuário> "<Nome>"
// Cria (ou troca a senha de) um cadastro da plataforma: a equipe Condo-mini, que
// cadastra administradoras e aloca condomínios. A senha é pedida no terminal.

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    exit;
}

require dirname(__DIR__) . '/lib/core.php';

[$login, $name] = [trim($argv[1] ?? ''), trim($argv[2] ?? '')];
if ($login === '') {
    fwrite(STDERR, "Uso: php scripts/platform-user.php <usuário> \"<Nome>\"\n");
    exit(1);
}
echo 'Senha (mínimo 10 caracteres): ';
$password = trim((string) fgets(STDIN));
if (mb_strlen($password) < 10) {
    fwrite(STDERR, "Senha curta demais.\n");
    exit(1);
}

$existing = row('SELECT * FROM users WHERE login_key = ?', [login_key($login)]);
if ($existing !== null && $existing['role'] !== 'PLATFORM') {
    fwrite(STDERR, "Este usuário já existe com outro perfil.\n");
    exit(1);
}
$hash = password_hash($password, PASSWORD_DEFAULT);
if ($existing !== null) {
    exec_sql('UPDATE users SET password_hash = ? WHERE id = ?', [$hash, $existing['id']]);
    exec_sql('DELETE FROM sessions WHERE user_id = ?', [$existing['id']]);
    echo "Senha trocada; as sessões abertas foram encerradas.\n";
    exit;
}
exec_sql(
    "INSERT INTO users (id, condo_id, company_id, name, role, login, login_key, password_hash)
     VALUES (?, NULL, NULL, ?, 'PLATFORM', ?, ?, ?)",
    [uuid(), $name !== '' ? $name : 'Equipe Condo-mini', $login, login_key($login), $hash]
);
echo "Cadastro da plataforma criado: $login\n";
