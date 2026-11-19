<?php
// Arquivo de retorno do banco (CNAB 240 ou 400): lê os pagamentos e acha o boleto
// de cada um, para a administradora dar baixa de uma vez.

declare(strict_types=1);

/** Ocorrências que querem dizer "pago" (liquidação normal, parcial, em cartório, após baixa). */
const CNAB_PAID_CODES = ['06', '07', '08', '15', '17'];

/**
 * Pagamentos do arquivo, em qualquer um dos dois formatos. Cada item:
 * ['line' => nº da linha, 'number' => dígitos do "nosso número", 'face' => valor do título,
 *  'paid' => valor pago, 'date' => Y-m-d do pagamento, 'code' => ocorrência, 'isPayment' => bool]
 */
function parse_bank_return(string $text): array
{
    $lines = preg_split('/\r\n|\r|\n/', $text);
    $lines = array_values(array_filter($lines, fn ($l) => trim($l) !== ''));
    if ($lines === []) {
        throw new ApiError(422, 'O arquivo está vazio.');
    }
    $header = $lines[0];
    if (str_starts_with($header, '02RETORNO') || strlen($header) >= 390) {
        return parse_cnab400($lines);
    }
    if (strlen($header) >= 230 && $header[7] === '0') {
        return parse_cnab240($lines);
    }
    throw new ApiError(422, 'Não reconheci o arquivo. Envie o arquivo de retorno do banco (CNAB 240 ou 400), do jeito que o banco gera.');
}

/** "0000000071230" com 2 casas → 712.30 */
function cnab_money(string $digits): float
{
    return (int) preg_replace('/\D/', '', $digits) / 100;
}

/** DDMMAA ou DDMMAAAA → Y-m-d (null se vazio ou inválido). */
function cnab_date(string $raw): ?string
{
    $raw = trim($raw);
    if (!preg_match('/^\d{6}(\d{2})?$/', $raw) || (int) $raw === 0) {
        return null;
    }
    $year = strlen($raw) === 8 ? (int) substr($raw, 4, 4) : 2000 + (int) substr($raw, 4, 2);
    $month = (int) substr($raw, 2, 2);
    $day = (int) substr($raw, 0, 2);
    return checkdate($month, $day, $year) ? sprintf('%04d-%02d-%02d', $year, $month, $day) : null;
}

/** Posições do manual do banco (começam em 1). */
function cnab_field(string $line, int $from, int $to): string
{
    return substr(str_pad($line, $to), $from - 1, $to - $from + 1);
}

function parse_cnab400(array $lines): array
{
    $items = [];
    foreach ($lines as $i => $line) {
        if (($line[0] ?? '') !== '1') {
            continue;
        }
        $code = cnab_field($line, 109, 110);
        $items[] = [
            'line' => $i + 1,
            // O nosso número fica em posições diferentes em cada banco (Itaú 63–70, Bradesco 71–82,
            // Banco do Brasil 64–80, Caixa 57–73...): a região inteira serve para comparar.
            'number' => preg_replace('/\D/', '', cnab_field($line, 38, 94)),
            'face' => cnab_money(cnab_field($line, 153, 165)),
            'paid' => cnab_money(cnab_field($line, 254, 266)),
            'date' => cnab_date(cnab_field($line, 111, 116)),
            'code' => $code,
            'isPayment' => in_array($code, CNAB_PAID_CODES, true),
        ];
    }
    return $items;
}

function parse_cnab240(array $lines): array
{
    $items = [];
    foreach ($lines as $i => $line) {
        // Registro de detalhe (3), segmento T: título; o U logo depois traz valor pago e data.
        if (($line[7] ?? '') !== '3' || ($line[13] ?? '') !== 'T') {
            continue;
        }
        $u = $lines[$i + 1] ?? '';
        $hasU = ($u[7] ?? '') === '3' && ($u[13] ?? '') === 'U';
        $code = cnab_field($line, 16, 17);
        $items[] = [
            'line' => $i + 1,
            'number' => preg_replace('/\D/', '', cnab_field($line, 38, 57)),
            'face' => cnab_money(cnab_field($line, 82, 96)),
            'paid' => $hasU ? cnab_money(cnab_field($u, 78, 92)) : 0.0,
            'date' => $hasU ? cnab_date(cnab_field($u, 138, 145)) : null,
            'code' => $code,
            'isPayment' => in_array($code, CNAB_PAID_CODES, true),
        ];
    }
    return $items;
}

/** "Campo livre" da linha digitável (25 dígitos): é onde o banco põe o nosso número. */
function boleto_free_field(string $barcode): string
{
    $d = preg_replace('/\D/', '', $barcode);
    return strlen($d) === 47 ? substr($d, 4, 5) . substr($d, 10, 10) . substr($d, 21, 10) : '';
}

function longest_common_run(string $a, string $b): int
{
    $best = 0;
    $prev = array_fill(0, strlen($b) + 1, 0);
    for ($i = 1, $la = strlen($a); $i <= $la; $i++) {
        $cur = [0];
        for ($j = 1, $lb = strlen($b); $j <= $lb; $j++) {
            $cur[$j] = $a[$i - 1] === $b[$j - 1] ? $prev[$j - 1] + 1 : 0;
            $best = max($best, $cur[$j]);
        }
        $prev = $cur;
    }
    return $best;
}

/**
 * O boleto do pagamento: entre os do condomínio com o mesmo valor, o que tem o
 * campo livre mais parecido com o nosso número. Agência e conta se repetem em todos,
 * então quem decide é o trecho do nosso número; empate ou pouca semelhança = sem boleto.
 */
function match_bill(array $payment, array $bills): ?array
{
    $scores = [];
    foreach ($bills as $bill) {
        if (abs((float) $bill['amount'] - $payment['face']) > 0.005) {
            continue;
        }
        $free = boleto_free_field($bill['barcode']);
        if ($free !== '') {
            $scores[] = [longest_common_run(ltrim($payment['number'], '0') ?: '0', $free), $bill];
        }
    }
    usort($scores, fn ($x, $y) => $y[0] <=> $x[0]);
    if ($scores === [] || $scores[0][0] < 6 || (isset($scores[1]) && $scores[1][0] === $scores[0][0])) {
        return null;
    }
    return $scores[0][1];
}
