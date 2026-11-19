<?php
// "Esqueci a senha": a pessoa pede um código no login, recebe no e-mail do cadastro
// e escolhe a senha nova sozinha, sem passar pela administração.

declare(strict_types=1);

const RESET_CODE_MINUTES = 15;
const RESET_MAX_ATTEMPTS = 5;
const RESET_RESEND_SECONDS = 60;

/**
 * config.php → 'mail' => [
 *   'from' => caixa de e-mail criada na Hostinger (ex.: nao-responda@seudominio.com.br),
 *   'from_name' => nome que aparece como remetente (padrão "Mini Condo"),
 *   'log_file' => opcional: grava os e-mails em vez de enviar (testes locais),
 * ]
 * Sem a chave 'mail', a recuperação por e-mail fica desligada.
 */
function mail_config(): array
{
    return config()['mail'] ?? [];
}

function send_mail(string $to, string $subject, string $text): bool
{
    $config = mail_config();
    if (isset($config['log_file'])) {
        return file_put_contents($config['log_file'], date('c') . " Para: $to\nAssunto: $subject\n$text\n\n", FILE_APPEND) !== false;
    }
    $from = (string) ($config['from'] ?? '');
    if ($from === '') {
        return false;
    }
    $name = mb_encode_mimeheader((string) ($config['from_name'] ?? 'Mini Condo'), 'UTF-8');
    $headers = implode("\r\n", [
        "From: $name <$from>",
        "Reply-To: $from",
        'MIME-Version: 1.0',
        'Content-Type: text/plain; charset=UTF-8',
        'Content-Transfer-Encoding: 8bit',
    ]);
    return mail($to, mb_encode_mimeheader($subject, 'UTF-8'), $text, $headers, '-f' . $from);
}

/** "ana.ribeiro@email.com" → "an•••@email.com", para a pessoa saber onde procurar. */
function masked_email(string $email): string
{
    [$local, $domain] = array_pad(explode('@', $email, 2), 2, '');
    return mb_substr($local, 0, 2) . '•••@' . $domain;
}

function reset_code_hash(string $userId, string $code): string
{
    return hash('sha256', $userId . ':' . $code);
}

function user_for_reset(): array
{
    $login = trim((string) input('login', ''));
    $user = $login === '' ? null : row('SELECT * FROM users WHERE login_key = ?', [login_key($login)]);
    if ($user === null) {
        throw new ApiError(404, 'Usuário não encontrado. Confira como está escrito.');
    }
    return $user;
}

function register_recovery_routes(Router $r): void
{
    $r->add('POST', '/auth/password-reset', function () {
        if (mail_config() === []) {
            throw new ApiError(503, 'A recuperação por e-mail não está disponível. Fale com a administração do condomínio.');
        }
        $user = user_for_reset();
        $email = trim((string) $user['email']);
        if ($email === '' || !str_contains($email, '@')) {
            throw new ApiError(422, 'Seu cadastro não tem e-mail. Fale com a administração do condomínio para trocar a senha.');
        }
        require_company_active($user);
        $previous = row('SELECT created_at FROM password_resets WHERE user_id = ?', [$user['id']]);
        if ($previous !== null && strtotime($previous['created_at']) > time() - RESET_RESEND_SECONDS) {
            throw new ApiError(429, 'Acabamos de enviar um código. Confira o e-mail ou espere um minuto para pedir outro.');
        }
        $code = str_pad((string) random_int(0, 999999), 6, '0', STR_PAD_LEFT);
        exec_sql(
            'REPLACE INTO password_resets (user_id, code_hash, expires_at, attempts, created_at) VALUES (?, ?, ?, 0, ?)',
            [$user['id'], reset_code_hash($user['id'], $code), date('Y-m-d H:i:s', time() + RESET_CODE_MINUTES * 60), now_sql()]
        );
        $first = explode(' ', trim((string) $user['name']))[0];
        $sent = send_mail(
            $email,
            "Seu código Mini Condo: $code",
            "Olá, $first.\n\n"
            . "Seu código para criar uma senha nova no Mini Condo é:\n\n    $code\n\n"
            . 'Ele vale por ' . RESET_CODE_MINUTES . " minutos. Digite no app, na tela de login, e escolha a senha nova.\n\n"
            . "Se você não pediu, ignore este e-mail: sua senha continua a mesma.\n\n— Mini Condo"
        );
        if (!$sent) {
            exec_sql('DELETE FROM password_resets WHERE user_id = ?', [$user['id']]);
            throw new ApiError(502, 'Não foi possível enviar o e-mail agora. Tente de novo em instantes.');
        }
        send_json(['ok' => true, 'email' => masked_email($email), 'minutes' => RESET_CODE_MINUTES]);
    });

    // Código certo: troca a senha e encerra as sessões abertas em outros aparelhos.
    $r->add('POST', '/auth/password-reset/confirm', function () {
        $user = user_for_reset();
        $code = preg_replace('/\D/', '', (string) input('code', ''));
        $password = (string) input('password', '');
        if (mb_strlen($password) < 6) {
            throw new ApiError(422, 'A senha precisa ter pelo menos 6 caracteres.');
        }
        $reset = row('SELECT * FROM password_resets WHERE user_id = ?', [$user['id']]);
        if ($reset === null || strtotime($reset['expires_at']) < time() || (int) $reset['attempts'] >= RESET_MAX_ATTEMPTS) {
            throw new ApiError(410, 'Este código venceu. Peça um código novo.');
        }
        if (!hash_equals($reset['code_hash'], reset_code_hash($user['id'], $code))) {
            exec_sql('UPDATE password_resets SET attempts = attempts + 1 WHERE user_id = ?', [$user['id']]);
            $left = RESET_MAX_ATTEMPTS - (int) $reset['attempts'] - 1;
            throw new ApiError(422, $left > 0
                ? "Código incorreto. Confira o e-mail ($left " . ($left === 1 ? 'tentativa restante' : 'tentativas restantes') . ').'
                : 'Código incorreto. Peça um código novo.');
        }
        transaction(function () use ($user, $password) {
            exec_sql('UPDATE users SET password_hash = ? WHERE id = ?', [password_hash($password, PASSWORD_DEFAULT), $user['id']]);
            exec_sql('DELETE FROM password_resets WHERE user_id = ?', [$user['id']]);
            exec_sql('DELETE FROM sessions WHERE user_id = ?', [$user['id']]);
        });
        send_json(['ok' => true]);
    });
}
