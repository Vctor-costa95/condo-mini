<?php
// Gera PDFs de boletos FICTÍCIOS para testar o envio em lote do painel.
// Uso: php scripts/gerar-boletos-teste.php [pasta de saída, padrão dist/boletos-teste]
// Feito para o condomínio "Testadores" (Predio de testes, 9 andares x 6 aptos) da produção.
// Traz de propósito duas exceções: o 905 sem apartamento e o 906 sem linha digitável.

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    exit;
}

$out = rtrim($argv[1] ?? dirname(__DIR__) . '/dist/boletos-teste', '/\\');
@mkdir($out, 0777, true);
@mkdir("$out/um-por-apartamento", 0777, true);

function mod10(string $s): int
{
    $sum = 0; $w = 2;
    for ($i = strlen($s) - 1; $i >= 0; $i--) { $p = (int) $s[$i] * $w; $sum += intdiv($p, 10) + $p % 10; $w = $w === 2 ? 1 : 2; }
    return (10 - $sum % 10) % 10;
}

/** Linha digitável válida (banco 341), vencimento 10/12/2026. */
function boleto_line(int $cents, int $seed): string
{
    $factor = 1000 + (int) (new DateTimeImmutable('2025-02-22'))->diff(new DateTimeImmutable('2026-12-10'))->days;
    $free = str_pad((string) (1790010104351 + $seed), 13, '0', STR_PAD_LEFT) . '004791020150';
    $tail = sprintf('%04d%010d', $factor, $cents);
    $body = '3419' . $tail . $free;
    $sum = 0; $w = 2;
    for ($i = strlen($body) - 1; $i >= 0; $i--) { $sum += (int) $body[$i] * $w; $w = $w === 9 ? 2 : $w + 1; }
    $dv = 11 - $sum % 11;
    if (in_array($dv, [0, 10, 11], true)) $dv = 1;
    $f1 = '3419' . substr($free, 0, 5); $f2 = substr($free, 5, 10); $f3 = substr($free, 15, 10);
    $d = $f1 . mod10($f1) . $f2 . mod10($f2) . $f3 . mod10($f3) . $dv . $tail;
    return sprintf('%s.%s %s.%s %s.%s %s %s', substr($d, 0, 5), substr($d, 5, 5), substr($d, 10, 5), substr($d, 15, 6), substr($d, 21, 5), substr($d, 26, 6), $d[32], substr($d, 33));
}

/** PDF simples: cada página é uma lista de linhas de texto (Helvetica). */
function write_pdf(string $path, array $pages): void
{
    $objects = [];
    $objects[1] = '<< /Type /Catalog /Pages 2 0 R >>';
    $objects[3] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>';
    $objects[4] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>';
    $kids = [];
    $next = 5;
    foreach ($pages as $lines) {
        $stream = '';
        $y = 790;
        foreach ($lines as $line) {
            [$text, $size, $bold] = is_array($line) ? $line + [1 => 11, 2 => false] : [$line, 11, false];
            $text = str_replace(['\\', '(', ')'], ['\\\\', '\\(', '\\)'], mb_convert_encoding($text, 'Windows-1252', 'UTF-8'));
            $stream .= sprintf("BT /%s %d Tf 50 %d Td (%s) Tj ET\n", $bold ? 'F2' : 'F1', $size, $y, $text);
            $y -= $size + 12;
        }
        $content = $next++;
        $page = $next++;
        $objects[$content] = "<< /Length " . strlen($stream) . " >>\nstream\n$stream\nendstream";
        $objects[$page] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $content 0 R >>";
        $kids[] = "$page 0 R";
    }
    $objects[2] = '<< /Type /Pages /Kids [' . implode(' ', $kids) . '] /Count ' . count($kids) . ' >>';
    ksort($objects);
    $pdf = "%PDF-1.4\n";
    $offsets = [];
    foreach ($objects as $n => $body) {
        $offsets[$n] = strlen($pdf);
        $pdf .= "$n 0 obj\n$body\nendobj\n";
    }
    $xref = strlen($pdf);
    $pdf .= "xref\n0 " . (count($objects) + 1) . "\n0000000000 65535 f \n";
    foreach ($offsets as $offset) $pdf .= sprintf("%010d 00000 n \n", $offset);
    $pdf .= "trailer\n<< /Size " . (count($objects) + 1) . " /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n";
    file_put_contents($path, $pdf);
}

function boleto_page(string $unitText, int $cents, int $seed): array
{
    return [
        ['BOLETO FICTICIO - SOMENTE PARA TESTE', 9, true],
        ['Condominio Testadores - Predio de testes', 14, true],
        'Beneficiario: Administradora Testes',
        'Pagador: Morador de teste',
        $unitText,
        'Referencia: dezembro/2026',
        'Vencimento: 10/12/2026     Valor: R$ ' . number_format($cents / 100, 2, ',', '.'),
        '',
        ['Linha digitavel:', 10, true],
        [boleto_line($cents, $seed), 12, false],
    ];
}

// 1) Um PDF com o prédio inteiro, como o sistema de cobrança gera.
$formats = [
    fn ($n) => "Unidade: Apto $n",
    fn ($n) => "Apto $n - Predio de testes",
    fn ($n) => "Unidade $n",
    fn ($n) => "Apartamento no $n",
];
$pages = [];
$seed = 0;
for ($floor = 1; $floor <= 9; $floor++) {
    for ($pos = 1; $pos <= 6; $pos++) {
        $n = $floor . '0' . $pos;
        $cents = 72000 + $floor * 100 + $pos * 10;
        if ($n === '905') {
            // Exceção de propósito: o boleto não diz o apartamento.
            $pages[] = boleto_page('Unidade: nao informada', $cents, $seed++);
            continue;
        }
        if ($n === '906') {
            // Exceção de propósito: boleto sem linha digitável.
            $page = boleto_page("Unidade: Apto $n", $cents, $seed++);
            array_splice($page, 8);
            $page[] = 'Linha digitavel: indisponivel neste boleto';
            $pages[] = $page;
            continue;
        }
        $pages[] = boleto_page($formats[$seed % count($formats)]($n), $cents, $seed++);
        if ($n === '201') {
            $pages[] = [['Verso do boleto do Apto 201', 12, true], 'Instrucoes: nao receber apos 30 dias do vencimento.', 'Esta pagina deve ir junto com o boleto do 201.'];
        }
    }
}
write_pdf("$out/testadores-boletos-dezembro-2026.pdf", $pages);

// 2) Um arquivo por apartamento (1º andar), com o apartamento só no nome do arquivo.
for ($pos = 1; $pos <= 6; $pos++) {
    $n = "10$pos";
    write_pdf("$out/um-por-apartamento/$n.pdf", [boleto_page('Unidade: ver nome do arquivo', 69000 + $pos * 10, 500 + $pos)]);
}
echo "ok\n";
