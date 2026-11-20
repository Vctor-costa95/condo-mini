<?php
// "Esqueci a senha": a pessoa pede um código no login, recebe no e-mail do cadastro
// e escolhe a senha nova sozinha, sem passar pela administração.

declare(strict_types=1);

const RESET_CODE_MINUTES = 15;
const RESET_MAX_ATTEMPTS = 5;
const RESET_RESEND_SECONDS = 60;
// Códigos por pessoa a cada 24 horas. Com 5 tentativas cada, são 25 chutes por dia no
// máximo (antes, um código novo por minuto zerava as tentativas).
const RESET_MAX_CODES_PER_DAY = 5;

/**
 * config.php → 'mail' => [
 *   'from' => caixa de e-mail criada na Hostinger (ex.: nao-responda@seudominio.com.br),
 *   'from_name' => nome que aparece como remetente (padrão "Mini Condo"),
 *   'log_file' => opcional: grava os e-mails em vez de enviar (testes locais),
 *   'smtp' => opcional, mas recomendado: [
 *       'host' => 'smtp.hostinger.com', 'port' => 465, 'secure' => 'ssl' (ou 'tls' na 587, 'none' só em teste),
 *       'user' => a mesma caixa de 'from', 'pass' => senha dessa caixa,
 *   ],
 * ]
 * Com 'smtp', o e-mail sai autenticado pela caixa (assinado pelo domínio, sem o
 * "por srv....main-hosting.eu" no Gmail e com menos chance de spam); sem, vai pelo mail() do PHP.
 * Sem a chave 'mail', a recuperação por e-mail fica desligada.
 */
function mail_config(): array
{
    return config()['mail'] ?? [];
}

/**
 * Manda em duas versões (multipart/alternative): o HTML com a identidade do app e o
 * texto puro, que aparece em quem não mostra HTML e ajuda a não cair no spam.
 */
function send_mail(string $to, string $subject, string $text, ?string $html = null): bool
{
    $config = mail_config();
    // Endereço gravado antes da validação pode ter quebra de linha: nunca vai para o SMTP.
    if (!is_valid_email($to)) {
        error_log('[condomini] e-mail recusado: endereço inválido');
        return false;
    }
    if (isset($config['log_file'])) {
        if ($html !== null) {
            // Para ver como fica: abra o .html ao lado do log no navegador.
            file_put_contents($config['log_file'] . '.html', $html);
        }
        return file_put_contents($config['log_file'], date('c') . " Para: $to\nAssunto: $subject\n$text\n\n", FILE_APPEND) !== false;
    }
    $from = (string) ($config['from'] ?? '');
    if ($from === '') {
        return false;
    }
    $name = mb_encode_mimeheader((string) ($config['from_name'] ?? 'Mini Condo'), 'UTF-8');
    $headers = ["From: $name <$from>", "Reply-To: $from", 'MIME-Version: 1.0'];
    if ($html === null) {
        $headers[] = 'Content-Type: text/plain; charset=UTF-8';
        $headers[] = 'Content-Transfer-Encoding: base64';
        $body = chunk_split(base64_encode($text));
    } else {
        $boundary = 'mc_' . bin2hex(random_bytes(12));
        $headers[] = "Content-Type: multipart/alternative; boundary=\"$boundary\"";
        $body = '';
        foreach (['text/plain' => $text, 'text/html' => $html] as $type => $content) {
            $body .= "--$boundary\r\nContent-Type: $type; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                . chunk_split(base64_encode($content)) . "\r\n";
        }
        $body .= "--$boundary--\r\n";
    }
    $encodedSubject = mb_encode_mimeheader($subject, 'UTF-8');
    if (isset($config['smtp'])) {
        $domain = substr($from, strrpos($from, '@') + 1);
        array_unshift(
            $headers,
            'Date: ' . date('r'),
            'Message-ID: <' . bin2hex(random_bytes(16)) . "@$domain>",
            "To: <$to>",
            "Subject: $encodedSubject"
        );
        try {
            smtp_send($config['smtp'], $from, $to, implode("\r\n", $headers) . "\r\n\r\n" . $body);
            return true;
        } catch (RuntimeException $e) {
            error_log('[condomini] SMTP: ' . $e->getMessage());
            return false;
        }
    }
    return mail($to, $encodedSubject, $body, implode("\r\n", $headers), '-f' . $from);
}

/**
 * Cliente SMTP mínimo (AUTH LOGIN), sem biblioteca: conecta, autentica com a caixa e
 * entrega uma mensagem já montada. Lança RuntimeException com a resposta do servidor.
 */
function smtp_send(array $smtp, string $from, string $to, string $message): void
{
    $host = (string) ($smtp['host'] ?? 'smtp.hostinger.com');
    $secure = (string) ($smtp['secure'] ?? 'ssl');
    $port = (int) ($smtp['port'] ?? ($secure === 'ssl' ? 465 : 587));
    $target = ($secure === 'ssl' ? 'ssl://' : 'tcp://') . "$host:$port";
    $socket = @stream_socket_client($target, $errno, $errstr, 15, STREAM_CLIENT_CONNECT,
        stream_context_create(['ssl' => ['peer_name' => $host]]));
    if ($socket === false) {
        throw new RuntimeException("sem conexão com $target: $errstr");
    }
    stream_set_timeout($socket, 15);
    $read = function () use ($socket): array {
        $text = '';
        while (($line = fgets($socket, 1024)) !== false) {
            $text .= $line;
            if (strlen($line) < 4 || $line[3] !== '-') {
                break;
            }
        }
        return [(int) substr($text, 0, 3), trim($text)];
    };
    $command = function (?string $line, array $expect, bool $secret = false) use ($socket, $read): string {
        if ($line !== null) {
            fwrite($socket, $line . "\r\n");
        }
        [$code, $reply] = $read();
        if (!in_array($code, $expect, true)) {
            // Usuário e senha nunca vão para o log.
            $shown = $line === null ? 'conexão' : ($secret ? '(login da caixa)' : $line);
            throw new RuntimeException("$shown → $reply");
        }
        return $reply;
    };
    try {
        $command(null, [220]);
        $ehlo = 'EHLO ' . (gethostname() ?: 'localhost');
        $command($ehlo, [250]);
        if ($secure === 'tls') {
            $command('STARTTLS', [220]);
            if (!stream_socket_enable_crypto($socket, true, STREAM_CRYPTO_METHOD_TLS_CLIENT)) {
                throw new RuntimeException('STARTTLS falhou');
            }
            $command($ehlo, [250]);
        }
        if (($smtp['user'] ?? '') !== '') {
            $command('AUTH LOGIN', [334]);
            $command(base64_encode((string) $smtp['user']), [334], true);
            $command(base64_encode((string) ($smtp['pass'] ?? '')), [235], true);
        }
        $command("MAIL FROM:<$from>", [250]);
        $command("RCPT TO:<$to>", [250, 251]);
        $command('DATA', [354]);
        // Linha que começa com "." ganha outro ponto (o servidor tira de volta).
        $data = preg_replace('/^\./m', '..', str_replace(["\r\n", "\n"], ["\n", "\r\n"], $message));
        $command(rtrim($data, "\r\n") . "\r\n.", [250]);
        $command('QUIT', [221]);
    } finally {
        fclose($socket);
    }
}

/**
 * E-mail do código, com a cara do app: topo azul-noite com a marca, o código grande
 * num bloco champanhe e o aviso de segurança. Tabelas e estilos na linha porque é
 * o que Gmail, Outlook e o Mail do iPhone entendem; fontes do sistema, sem nada de fora
 * além da logo (PNG servido pelo próprio painel).
 */
function reset_code_email_html(string $firstName, string $login, string $context, string $code): string
{
    $e = fn (string $value) => htmlspecialchars($value, ENT_QUOTES, 'UTF-8');
    $logo = base_url() . '/painel/img/email-icone.png';
    $minutes = RESET_CODE_MINUTES;
    $digits = implode('&nbsp;', str_split($code));
    $serif = "Georgia, 'Times New Roman', serif";
    $sans = "-apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";
    $who = $e($login) . ($context !== '' ? ' &middot; ' . $e($context) : '');
    return <<<HTML
<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light">
<title>Seu código Mini Condo</title>
</head>
<body style="margin:0;padding:0;background:#F0ECE4;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;">Seu código é {$code}. Ele vale por {$minutes} minutos.</div>
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#F0ECE4;">
<tr><td align="center" style="padding:32px 16px;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#FFFFFF;border-radius:20px;overflow:hidden;border:1px solid #E4DED3;">
    <tr><td style="background:#151B26;background-image:linear-gradient(135deg,#232B3A,#151B26);padding:28px 32px;">
      <table role="presentation" cellspacing="0" cellpadding="0"><tr>
        <td style="vertical-align:middle;"><img src="{$logo}" width="52" height="52" alt="" style="display:block;border:0;border-radius:12px;"></td>
        <td style="vertical-align:middle;padding-left:14px;">
          <div style="font-family:{$serif};font-size:22px;letter-spacing:3px;color:#F7F5F0;line-height:1;">MINI <span style="color:#D9BE8E;">CONDO</span></div>
          <div style="font-family:{$sans};font-size:10px;letter-spacing:3px;color:#B7BCC6;margin-top:7px;">GESTÃO DE CONDOMÍNIOS</div>
        </td>
      </tr></table>
    </td></tr>
    <tr><td style="height:3px;background:#B08D57;font-size:0;line-height:0;">&nbsp;</td></tr>
    <tr><td style="padding:36px 32px 8px;font-family:{$sans};color:#151B26;">
      <div style="font-size:11px;letter-spacing:2.5px;font-weight:600;color:#B08D57;">CÓDIGO DE ACESSO</div>
      <h1 style="margin:10px 0 0;font-family:{$serif};font-size:28px;font-weight:normal;line-height:1.2;color:#151B26;">Olá, {$e($firstName)}.</h1>
      <p style="margin:14px 0 0;font-size:15px;line-height:1.6;color:#5E6675;">Recebemos um pedido para criar uma senha nova na sua conta do Mini Condo. Use o código abaixo no app.</p>
    </td></tr>
    <tr><td style="padding:24px 32px 4px;">
      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#F3EADB;border:1px solid #E2CFAE;border-radius:14px;">
        <tr><td align="center" style="padding:24px 16px 20px;">
          <div style="font-family:{$sans};font-size:11px;letter-spacing:2px;color:#8C6A36;font-weight:600;">SEU CÓDIGO</div>
          <div style="font-family:{$sans};font-size:36px;font-weight:600;letter-spacing:6px;font-variant-numeric:lining-nums tabular-nums;color:#151B26;margin-top:8px;">{$digits}</div>
          <div style="font-family:{$sans};font-size:13px;color:#5E6675;margin-top:10px;">Vale por {$minutes} minutos</div>
        </td></tr>
      </table>
    </td></tr>
    <tr><td style="padding:24px 32px 0;font-family:{$sans};">
      <div style="font-size:14px;font-weight:600;color:#151B26;">Como usar</div>
      <p style="margin:8px 0 0;font-size:14px;line-height:1.6;color:#5E6675;">No app Mini Condo, na tela de login, toque em <strong style="color:#151B26;">Esqueceu a senha?</strong>, digite o código e escolha a senha nova. Depois disso, os outros celulares conectados à sua conta pedem login de novo.</p>
      <p style="margin:14px 0 0;font-size:13px;line-height:1.5;color:#8C93A0;">Conta: {$who}</p>
    </td></tr>
    <tr><td style="padding:24px 32px 32px;">
      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#F7F5F0;border-radius:12px;">
        <tr><td style="padding:16px 18px;font-family:{$sans};font-size:13px;line-height:1.55;color:#5E6675;">
          <strong style="color:#151B26;">Não foi você?</strong> Ignore este e-mail: sua senha continua a mesma. A equipe do Mini Condo e a administração do condomínio nunca pedem este código por telefone, WhatsApp ou e-mail.
        </td></tr>
      </table>
    </td></tr>
  </table>
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;">
    <tr><td align="center" style="padding:20px 16px 0;font-family:{$sans};font-size:12px;line-height:1.6;color:#8C93A0;">
      Mini Condo &middot; Gestão de condomínios<br>
      E-mail automático, enviado porque alguém pediu um código para esta conta. Não é preciso responder.
    </td></tr>
  </table>
</td></tr>
</table>
</body>
</html>
HTML;
}

/** "Residencial Jardins" para quem mora; o nome da administradora para o administrador. */
function reset_email_context(array $user): string
{
    if (!empty($user['condo_id'])) {
        return (string) (row('SELECT name FROM condominiums WHERE id = ?', [$user['condo_id']])['name'] ?? '');
    }
    $companyId = user_company_id($user);
    return $companyId === null ? '' : (string) (row('SELECT name FROM companies WHERE id = ?', [$companyId])['name'] ?? '');
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
        if ($email === '' || !is_valid_email($email)) {
            throw new ApiError(422, 'Seu cadastro não tem e-mail. Fale com a administração do condomínio para trocar a senha.');
        }
        require_company_active($user);
        $previous = row('SELECT created_at, codes_sent, window_started_at FROM password_resets WHERE user_id = ?', [$user['id']]);
        if ($previous !== null && strtotime($previous['created_at']) > time() - RESET_RESEND_SECONDS) {
            throw new ApiError(429, 'Acabamos de enviar um código. Confira o e-mail ou espere um minuto para pedir outro.');
        }
        $windowStart = date('Y-m-d H:i:s', time() - 86400);
        if ($previous !== null && $previous['window_started_at'] !== null && $previous['window_started_at'] > $windowStart
            && (int) $previous['codes_sent'] >= RESET_MAX_CODES_PER_DAY) {
            throw new ApiError(429, 'Você já pediu ' . RESET_MAX_CODES_PER_DAY . ' códigos nas últimas 24 horas. Tente amanhã ou fale com a administração do condomínio.');
        }
        $code = str_pad((string) random_int(0, 999999), 6, '0', STR_PAD_LEFT);
        // Código novo zera as tentativas, mas não a contagem de códigos do dia. No MySQL,
        // as atribuições do UPDATE valem na ordem: codes_sent olha a janela antiga.
        exec_sql(
            'INSERT INTO password_resets (user_id, code_hash, expires_at, attempts, codes_sent, window_started_at, created_at)
             VALUES (:id, :hash, :expires, 0, 1, :window, :created)
             ON DUPLICATE KEY UPDATE
                code_hash = VALUES(code_hash), expires_at = VALUES(expires_at), attempts = 0,
                codes_sent = IF(window_started_at > :cutoff, codes_sent + 1, 1),
                window_started_at = IF(window_started_at > :cutoff2, window_started_at, VALUES(window_started_at)),
                created_at = VALUES(created_at)',
            [
                'id' => $user['id'],
                'hash' => reset_code_hash($user['id'], $code),
                'expires' => date('Y-m-d H:i:s', time() + RESET_CODE_MINUTES * 60),
                // Sem EMULATE_PREPARES, cada marcador só pode aparecer uma vez.
                'window' => now_sql(),
                'created' => now_sql(),
                'cutoff' => $windowStart,
                'cutoff2' => $windowStart,
            ]
        );
        $first = explode(' ', trim((string) $user['name']))[0];
        $context = reset_email_context($user);
        $sent = send_mail(
            $email,
            "Seu código Mini Condo: $code",
            "Olá, $first.\n\n"
            . "Seu código para criar uma senha nova no Mini Condo é:\n\n    $code\n\n"
            . 'Ele vale por ' . RESET_CODE_MINUTES . " minutos. No app, na tela de login, toque em \"Esqueceu a senha?\", digite o código e escolha a senha nova.\n\n"
            . 'Conta: ' . $user['login'] . ($context !== '' ? " · $context" : '') . "\n\n"
            . "Não foi você? Ignore este e-mail: sua senha continua a mesma. Ninguém do Mini Condo ou da administração pede este código por telefone, WhatsApp ou e-mail.\n\n"
            . "— Mini Condo · Gestão de condomínios",
            reset_code_email_html($first, (string) $user['login'], $context, $code)
        );
        if (!$sent) {
            // Invalida o código e libera pedir outro na hora, sem zerar a contagem do dia.
            exec_sql(
                "UPDATE password_resets SET expires_at = '2000-01-01 00:00:00', created_at = '2000-01-01 00:00:00' WHERE user_id = ?",
                [$user['id']]
            );
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
        // Gasta a tentativa antes de comparar, numa só instrução: pedidos em paralelo não
        // conseguem passar todos pela checagem de "ainda tem tentativa".
        $claimed = exec_sql(
            'UPDATE password_resets SET attempts = attempts + 1 WHERE user_id = ? AND attempts < ? AND expires_at > ?',
            [$user['id'], RESET_MAX_ATTEMPTS, now_sql()]
        );
        $reset = row('SELECT * FROM password_resets WHERE user_id = ?', [$user['id']]);
        if ($claimed !== 1 || $reset === null) {
            throw new ApiError(410, 'Este código venceu. Peça um código novo.');
        }
        if (!hash_equals($reset['code_hash'], reset_code_hash($user['id'], $code))) {
            $left = RESET_MAX_ATTEMPTS - (int) $reset['attempts'];
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
