<?php
// Uso: php scripts/platform-user.php <usuário> "<Nome>" [--sql]
// Cria (ou troca a senha de) um cadastro da plataforma: a equipe Condo-mini, que
// cadastra administradoras e aloca condomínios. A senha é pedida no terminal.
//
// Com --sql, não conecta no banco: só imprime o comando para colar no phpMyAdmin
// (útil na Hostinger, onde o banco só aceita conexões do próprio servidor). Roda
// no seu computador, sem config.php. A senha não aparece no comando, só o hash dela.

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    exit;
}

require dirname(__DIR__) . '/lib/core.php';

$args = array_values(array_filter(array_slice($argv, 1), fn ($a) => $a !== '--sql'));
$sqlOnly = in_array('--sql', $argv, true);
[$login, $name] = [mb_strtolower(trim($args[0] ?? '')), trim($args[1] ?? '')];
if ($login === '' || preg_match('/\s/', $login)) {
    fwrite(STDERR, "Uso: php scripts/platform-user.php <usuário> \"<Nome>\" [--sql]\n");
    exit(1);
}
$name = $name !== '' ? $name : 'Equipe Condo-mini';
echo 'Senha (mínimo 10 caracteres): ';
$password = trim((string) fgets(STDIN));
if (mb_strlen($password) < 10) {
    fwrite(STDERR, "Senha curta demais.\n");
    exit(1);
}
$hash = password_hash($password, PASSWORD_DEFAULT);

if ($sqlOnly) {
    $q = fn (string $value) => "'" . str_replace(['\\', "'"], ['\\\\', "''"], $value) . "'";
    echo "\n-- Cole no phpMyAdmin (aba SQL) do banco do condo.mini. Se o usuário já existir, dá erro e nada muda.\n";
    echo 'INSERT INTO users (id, condo_id, company_id, name, role, login, login_key, password_hash) VALUES ('
        . implode(', ', [$q(uuid()), 'NULL', 'NULL', $q($name), "'PLATFORM'", $q($login), $q(login_key($login)), $q($hash)])
        . ");\n";
    exit;
}

$existing = row('SELECT * FROM users WHERE login_key = ?', [login_key($login)]);
if ($existing !== null && $existing['role'] !== 'PLATFORM') {
    fwrite(STDERR, "Este usuário já existe com outro perfil.\n");
    exit(1);
}
if ($existing !== null) {
    exec_sql('UPDATE users SET password_hash = ? WHERE id = ?', [$hash, $existing['id']]);
    exec_sql('DELETE FROM sessions WHERE user_id = ?', [$existing['id']]);
    echo "Senha trocada; as sessões abertas foram encerradas.\n";
    exit;
}
exec_sql(
    "INSERT INTO users (id, condo_id, company_id, name, role, login, login_key, password_hash)
     VALUES (?, NULL, NULL, ?, 'PLATFORM', ?, ?, ?)",
    [uuid(), $name, $login, login_key($login), $hash]
);
echo "Cadastro da plataforma criado: $login\n";
