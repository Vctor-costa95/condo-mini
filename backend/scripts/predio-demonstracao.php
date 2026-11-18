<?php
// Gera o prédio de DEMONSTRAÇÃO "Residencial Aurora" (administradora fictícia
// "Aurora Gestão Condominial"), para prints da landing e demonstrações.
//
// Uso: php scripts/predio-demonstracao.php [pasta de saída, padrão dist/demo]
// Sai:
//   predio-demonstracao.sql          rodar no phpMyAdmin (aba SQL) do banco de produção
//   remover-predio-demonstracao.sql  apaga tudo o que o arquivo acima criou
//   aurora-boletos-novembro-2026.pdf boletos FICTÍCIOS do mês, para enviar pelo painel
//                                    (Unidades → Enviar boletos do mês), com uma exceção
//                                    de propósito (página sem apartamento) para a conferência.
//
// Tudo é fictício. Os cadastros entram SEM senha (não conseguem entrar na produção):
// para tirar prints do app, defina a senha de quem precisar pelo painel, em Cadastros.
// Datas de avisos, reservas, encomendas e ocorrências são relativas ao momento em que
// o SQL roda (NOW()/CURDATE()), então o prédio parece "vivo" no dia em que for criado.

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    exit;
}

$out = rtrim($argv[1] ?? dirname(__DIR__) . '/dist/demo', '/\\');
@mkdir($out, 0777, true);
mt_srand(2026);

const CO = 'demo-aurora-co';
const CONDO = 'demo-aurora';
const TOWERS = [['demo-aurora-tn', 'Torre Norte'], ['demo-aurora-ts', 'Torre Sul']];
const FLOORS = 12;
const PER_FLOOR = 4;
const TOP_FLOOR_UNITS = 2; // coberturas

function q(?string $v): string
{
    return $v === null ? 'NULL' : "'" . str_replace(["\\", "'"], ["\\\\", "''"], $v) . "'";
}

function insert(string $table, array $row): string
{
    $vals = array_map(fn ($v) => is_array($v) ? $v[0] : (is_int($v) || is_float($v) ? (string) $v : q($v)), $row);
    return "INSERT INTO $table (" . implode(', ', array_keys($row)) . ') VALUES (' . implode(', ', $vals) . ");";
}

/** Expressão SQL crua (não vai entre aspas). */
function raw(string $sql): array
{
    return [$sql];
}

function at(string $interval): array
{
    return raw("DATE_ADD(NOW(), INTERVAL $interval)");
}

function day(int $offset): array
{
    return raw("DATE_ADD(CURDATE(), INTERVAL $offset DAY)");
}

function login_key(string $s): string
{
    $s = mb_strtolower(trim($s));
    return strtr($s, ['á' => 'a', 'à' => 'a', 'â' => 'a', 'ã' => 'a', 'é' => 'e', 'ê' => 'e',
        'í' => 'i', 'ó' => 'o', 'ô' => 'o', 'õ' => 'o', 'ú' => 'u', 'ü' => 'u', 'ç' => 'c']);
}

function mod10(string $s): int
{
    $sum = 0; $w = 2;
    for ($i = strlen($s) - 1; $i >= 0; $i--) { $p = (int) $s[$i] * $w; $sum += intdiv($p, 10) + $p % 10; $w = $w === 2 ? 1 : 2; }
    return (10 - $sum % 10) % 10;
}

/** Linha digitável válida (banco 341) para o valor e o vencimento dados. */
function boleto_line(int $cents, int $seed, string $due): string
{
    $factor = 1000 + (int) (new DateTimeImmutable('2025-02-22'))->diff(new DateTimeImmutable($due))->days;
    $free = str_pad((string) (1790020204351 + $seed), 13, '0', STR_PAD_LEFT) . '004791020150';
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

// ------------------------------------------------------------ apartamentos
$units = []; // [label, número, torre, é cobertura]
foreach (TOWERS as [, $tname]) {
    for ($floor = 1; $floor <= FLOORS; $floor++) {
        $count = $floor === FLOORS ? TOP_FLOOR_UNITS : PER_FLOOR;
        for ($p = 1; $p <= $count; $p++) {
            $n = $floor . str_pad((string) $p, 2, '0', STR_PAD_LEFT);
            $units[] = ["Apto $n - $tname", $n, $tname, $floor === FLOORS];
        }
    }
}
$fee = fn (array $u) => $u[3] ? 124000 : 78000 + ((int) substr($u[1], -2) % 2) * 3500; // centavos

// Apartamentos sem morador cadastrado (realista: nem todo mundo entrou no app ainda).
$empty = ['Apto 203 - Torre Norte', 'Apto 704 - Torre Norte', 'Apto 1101 - Torre Norte', 'Apto 302 - Torre Sul', 'Apto 903 - Torre Sul', 'Apto 1004 - Torre Sul'];

// ------------------------------------------------------------ pessoas
$first = ['Lucas', 'Mariana', 'Gabriel', 'Isabela', 'Rafael', 'Larissa', 'Thiago', 'Camila', 'Bruno', 'Letícia', 'Felipe', 'Amanda',
    'Diego', 'Natália', 'Rodrigo', 'Vanessa', 'Eduardo', 'Patrícia', 'Leonardo', 'Tatiane', 'Vinícius', 'Aline', 'Gustavo', 'Renata',
    'Matheus', 'Bianca', 'André', 'Priscila', 'Daniel', 'Carolina', 'Fábio', 'Juliana', 'Marcelo', 'Débora', 'Otávio', 'Lívia',
    'Henrique', 'Sabrina', 'Murilo', 'Elisa', 'Caio', 'Paula', 'Igor', 'Roberta', 'Samuel', 'Cecília', 'Vitor', 'Lorena'];
$last = ['Albuquerque', 'Monteiro', 'Cavalcanti', 'Sampaio', 'Figueiredo', 'Bittencourt', 'Queiroz', 'Medeiros', 'Valadares',
    'Assunção', 'Brandão', 'Siqueira', 'Guimarães', 'Pacheco', 'Tavora', 'Moura', 'Rezende', 'Dantas', 'Coutinho', 'Leal',
    'Fontes', 'Amaral', 'Carvalhal', 'Peixoto', 'Barreto', 'Sardinha', 'Holanda', 'Paiva', 'Aragão', 'Camargo'];

$sql = [];
$ids = 0;
$id = fn (string $p) => sprintf('demo-aurora-%s-%03d', $p, ++$GLOBALS['ids']);
$usedLogins = [];
$people = []; // [id, nome, role, units, email, phone]
$residentOf = []; // label => nome do primeiro morador
$newPerson = function (string $role, array $unitLabels, ?string $name = null, ?string $email = null, ?string $phone = null) use (&$usedLogins, &$people, &$residentOf, $first, $last, $id): string {
    do {
        $name ??= $first[mt_rand(0, count($first) - 1)] . ' ' . $last[mt_rand(0, count($last) - 1)];
        $parts = explode(' ', $name);
        $login = login_key($parts[0] . '.' . end($parts));
        if (isset($usedLogins[$login])) {
            $name = null;
        }
    } while ($name === null);
    $usedLogins[$login] = true;
    $uid = $id('u');
    $people[] = [$uid, $name, $role, $unitLabels, $email ?? str_replace(' ', '.', login_key($name)) . '@exemplo.com',
        $phone ?? sprintf('(11) 9%04d-%04d', mt_rand(1000, 9999), mt_rand(1000, 9999)), $login];
    foreach ($unitLabels as $l) {
        $residentOf[$l] ??= $name;
    }
    return $name;
};

$manager = $newPerson('MANAGER', ['Apto 801 - Torre Norte'], 'Renato Albuquerque', 'renato.albuquerque@exemplo.com', '(11) 98123-4410');
$newPerson('DOORMAN', [], 'Valdir Nascimento', 'portaria@residencialaurora.exemplo.com', '(11) 3456-7700');
$newPerson('DOORMAN', [], 'Edson Prates', 'portaria@residencialaurora.exemplo.com', '(11) 3456-7700');
$newPerson('ADMIN', [], 'Juliana Martins Rocha', 'juliana@auroragestao.exemplo.com', '(11) 97654-3210');
foreach ($units as [$label]) {
    if (in_array($label, $empty, true) || $label === 'Apto 801 - Torre Norte') {
        continue;
    }
    $name = $newPerson('RESIDENT', [$label]);
    if (mt_rand(1, 100) <= 22) { // casal no mesmo apartamento, mesmo sobrenome
        $sur = explode(' ', $name);
        $newPerson('RESIDENT', [$label], $first[mt_rand(0, count($first) - 1)] . ' ' . end($sur));
    }
}

// ------------------------------------------------------------ SQL
$sql[] = '-- Prédio de DEMONSTRAÇÃO "Residencial Aurora" (fictício). Gerado por backend/scripts/predio-demonstracao.php.';
$sql[] = '-- Rodar no phpMyAdmin, aba SQL. Para apagar tudo: remover-predio-demonstracao.sql.';
$sql[] = 'SET NAMES utf8mb4;';
$sql[] = 'START TRANSACTION;';
$sql[] = insert('companies', ['id' => CO, 'name' => 'Aurora Gestão Condominial', 'cnpj' => '41.208.553/0001-07', 'brand_color' => '#2F4A6D', 'created_at' => at('-8 MONTH')]);
$sql[] = insert('condominiums', ['id' => CONDO, 'company_id' => CO, 'name' => 'Residencial Aurora', 'address' => 'Rua das Acácias, 450 · Vila Mariana, São Paulo']);
foreach (TOWERS as $pos => [$tid, $tname]) {
    $sql[] = insert('towers', ['id' => $tid, 'condo_id' => CONDO, 'name' => $tname, 'floors' => FLOORS, 'units_per_floor' => PER_FLOOR,
        'floor_overrides' => json_encode([(string) FLOORS => TOP_FLOOR_UNITS]), 'position' => $pos]);
}
foreach ($people as [$uid, $name, $role, $unitLabels, $email, $phone, $login]) {
    $sql[] = insert('users', ['id' => $uid, 'condo_id' => $role === 'ADMIN' ? null : CONDO, 'company_id' => $role === 'ADMIN' ? CO : null,
        'name' => $name, 'role' => $role, 'email' => $email, 'phone' => $phone, 'login' => $login, 'login_key' => login_key($login), 'password_hash' => null]);
    foreach (array_values($unitLabels) as $i => $l) {
        $sql[] = insert('user_units', ['user_id' => $uid, 'unit' => $l, 'position' => $i]);
    }
}

// Pedidos de cadastro esperando aprovação (aparecem no painel e para o síndico).
foreach ([['Paulo Esteves', 'Apto 203 - Torre Norte', '-5 HOUR'], ['Marta Quintela', 'Apto 903 - Torre Sul', '-1 DAY']] as [$name, $unit, $when]) {
    $parts = explode(' ', $name);
    $login = login_key($parts[0] . '.' . end($parts));
    $sql[] = insert('signup_requests', ['id' => $id('sr'), 'condo_id' => CONDO, 'name' => $name, 'email' => str_replace(' ', '.', login_key($name)) . '@exemplo.com',
        'phone' => sprintf('(11) 9%04d-%04d', mt_rand(1000, 9999), mt_rand(1000, 9999)), 'login' => $login, 'login_key' => $login,
        'password_hash' => password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT), 'unit' => $unit, 'status' => 'PENDING', 'ip' => '', 'created_at' => at($when)]);
}

// Avisos
$author = "Síndico - $manager";
$notices = [
    ['Assembleia geral ordinária · 2 de dezembro', 'Convocamos todos os condôminos para a AGO no dia 2 de dezembro, às 19h30, no salão de festas. Pauta: prestação de contas de 2026, previsão orçamentária de 2027 e eleição do conselho fiscal.', 'MANAGER', $author, '-4 HOUR', 1],
    ['Limpeza das caixas d\'água nesta quinta', 'Na quinta-feira, das 9h às 14h, o fornecimento de água será interrompido nas duas torres para a limpeza semestral das caixas d\'água. Reserve água com antecedência.', 'IMPORTANT', 'Administração', '-1 DAY', 1],
    ['Piscina fechada para manutenção', 'A piscina ficará fechada até o dia 25 para a troca do aquecedor. Pedimos desculpas pelo transtorno.', 'MAINTENANCE', $author, '-2 DAY', 0],
    ['Confraternização de fim de ano', 'No dia 13 de dezembro, a partir das 17h, teremos a confraternização dos moradores no salão de festas e na churrasqueira. Inscreva sua família na portaria até o dia 6.', 'EVENT', 'Comissão de eventos', '-3 DAY', 0],
    ['Manutenção preventiva dos elevadores', 'Os elevadores sociais da Torre Sul passam por manutenção preventiva na segunda-feira, das 8h às 12h. Utilize o elevador de serviço.', 'MAINTENANCE', 'Administração', '-6 DAY', 0],
    ['Novo horário da coleta seletiva', 'A coleta de recicláveis passa a acontecer às terças e sextas. Deposite os recicláveis nos contêineres identificados no subsolo.', 'MANAGER', $author, '-11 DAY', 0],
];
foreach ($notices as [$title, $body, $cat, $by, $when, $pinned]) {
    $sql[] = insert('notices', ['id' => $id('n'), 'condo_id' => CONDO, 'title' => $title, 'body' => $body, 'category' => $cat, 'author' => $by, 'published_at' => at($when), 'pinned' => $pinned]);
}

// Espaços: [chave, nome, descrição, capacidade, taxa, regras, ícone, modo, abre, fecha, máx, interdição]
$areas = [
    ['salao', 'Salão de festas', 'Salão climatizado para 80 pessoas, com copa equipada e banheiros.', 80, 180, 'Som até 22h. Limpeza por conta do morador.', 'party', 'PERIOD', 8, 22, 2, null],
    ['churras', 'Churrasqueira gourmet', 'Churrasqueira com forno de pizza, pia e mesas para 30 pessoas.', 30, 90, 'Trazer carvão. Devolver a área limpa.', 'grill', 'PERIOD', 8, 22, 2, null],
    ['piscina', 'Piscina aquecida', 'Piscina adulto e infantil com deck e espreguiçadeiras.', 40, 0, 'Uso até 22h. Crianças acompanhadas.', 'pool', 'DAILY', 8, 22, 2, 'Troca do aquecedor até o dia 25.'],
    ['quadra', 'Quadra poliesportiva', 'Quadra coberta para futebol, vôlei e basquete.', 20, 0, 'Calçado adequado obrigatório.', 'court', 'HOURLY', 7, 22, 2, null],
    ['cowork', 'Coworking', 'Sala silenciosa com 6 estações, wi-fi e cabine de reunião.', 6, 0, 'Até 3 horas por reserva. Silêncio.', 'meeting', 'HOURLY', 7, 22, 3, null],
    ['brinq', 'Brinquedoteca', 'Espaço infantil com brinquedos e área de leitura.', 15, 0, 'Crianças até 10 anos, sempre com um responsável.', 'kids', 'PERIOD', 8, 22, 2, null],
];
$areaId = [];
foreach ($areas as [$key, $name, $desc, $cap, $fee2, $rules, $icon, $mode, $opens, $closes, $max, $closed]) {
    $areaId[$key] = $id('a');
    $sql[] = insert('common_areas', ['id' => $areaId[$key], 'condo_id' => CONDO, 'name' => $name, 'description' => $desc, 'capacity' => $cap, 'fee' => $fee2,
        'rules' => $rules, 'icon_key' => $icon, 'booking_mode' => $mode, 'opens_at' => $opens, 'closes_at' => $closes, 'max_hours' => $max, 'closed_reason' => $closed]);
}

// Reservas: [espaço, dias a partir de hoje, turno ou [início, fim], apto, situação, convidados]
$hours = ['MORNING' => [8, 12], 'AFTERNOON' => [13, 17], 'NIGHT' => [18, 22]];
$reservations = [
    ['salao', 3, 'NIGHT', 'Apto 502 - Torre Norte', 'CONFIRMED', ['Ana Clara Moura', 'Beatriz Fontes', 'Carlos Eduardo Paiva', 'Daniela Sampaio', 'Fernando Leal', 'Gabriela Rezende', 'Heitor Amaral', 'Isadora Brandão', 'João Pedro Siqueira', 'Karina Holanda', 'Luiz Fernando Dantas', 'Manuela Coutinho']],
    ['churras', 2, 'AFTERNOON', 'Apto 1003 - Torre Sul', 'CONFIRMED', ['Otávio Barreto', 'Paula Aragão', 'Rogério Camargo', 'Simone Peixoto', 'Tiago Medeiros']],
    ['salao', 10, 'NIGHT', 'Apto 401 - Torre Sul', 'PENDING', null],
    ['churras', 6, 'AFTERNOON', 'Apto 304 - Torre Norte', 'PENDING', null],
    ['quadra', 1, [18, 20], 'Apto 602 - Torre Sul', 'CONFIRMED', null],
    ['cowork', 0, [9, 12], 'Apto 1202 - Torre Norte', 'CONFIRMED', null],
    ['brinq', 4, 'MORNING', 'Apto 204 - Torre Sul', 'CONFIRMED', ['Laura Figueiredo', 'Miguel Queiroz', 'Helena Valadares']],
    ['quadra', 5, [9, 11], 'Apto 101 - Torre Norte', 'CONFIRMED', null],
];
foreach ($reservations as [$area, $offset, $slot, $unit, $status, $guests]) {
    [$period, $start, $end] = is_array($slot) ? [null, $slot[0], $slot[1]] : [$slot, ...$hours[$slot]];
    $sql[] = insert('reservations', ['id' => $id('r'), 'condo_id' => CONDO, 'area_id' => $areaId[$area], 'date' => day($offset), 'period' => $period,
        'start_hour' => $start, 'end_hour' => $end, 'unit' => $unit, 'status' => $status, 'resident_name' => $residentOf[$unit] ?? '',
        'guests' => $guests ? json_encode($guests, JSON_UNESCAPED_UNICODE) : null, 'created_at' => at('-1 DAY')]);
}

// Mudanças e reparos: [tipo, dias, turno, apto, situação, tipo de reparo, prestador]
$bookings = [
    ['MOVE', 2, 'MORNING', 'Apto 704 - Torre Sul', 'CONFIRMED', null, 'Transportes Ideal'],
    ['MOVE', 8, 'MORNING', 'Apto 1102 - Torre Norte', 'PENDING', null, 'Mudanças Rápidas'],
    ['REPAIR', 1, 'AFTERNOON', 'Apto 903 - Torre Norte', 'PENDING', 'INTERNET', 'Vivo Fibra'],
    ['REPAIR', 0, 'MORNING', 'Apto 402 - Torre Sul', 'CONFIRMED', 'PLUMBING', 'Hidráulica Souza'],
    ['REPAIR', 3, 'AFTERNOON', 'Apto 603 - Torre Norte', 'CONFIRMED', 'ELECTRICAL', 'Eletricista Marcos'],
];
foreach ($bookings as [$type, $offset, $period, $unit, $status, $kind, $provider]) {
    $sql[] = insert('service_bookings', ['id' => $id('s'), 'condo_id' => CONDO, 'type' => $type, 'date' => day($offset), 'period' => $period, 'unit' => $unit,
        'resident_name' => $residentOf[$unit] ?? '', 'status' => $status, 'repair_kind' => $kind, 'provider' => $provider, 'created_at' => at('-1 DAY')]);
}

// Encomendas: [descrição, transportadora, porteiro, chegou, código, apto, retirada]
$parcels = [
    ['Caixa média', 'Correios', 'Porteiro Valdir', '-1 HOUR', '4821', 'Apto 502 - Torre Norte', null],
    ['Envelope', 'Mercado Livre', 'Porteiro Valdir', '-3 HOUR', '1937', 'Apto 1003 - Torre Sul', null],
    ['Caixa grande', 'Amazon', 'Porteiro Edson', '-6 HOUR', '7710', 'Apto 204 - Torre Sul', null],
    ['Pacote', 'Shopee', 'Porteiro Edson', '-20 HOUR', '2264', 'Apto 101 - Torre Norte', null],
    ['Caixa de vinhos', 'Evino', 'Porteiro Valdir', '-1 DAY', '5306', 'Apto 1202 - Torre Norte', null],
    ['Envelope', 'Correios', 'Porteiro Edson', '-2 DAY', '3158', 'Apto 602 - Torre Sul', null],
    ['Caixa pequena', 'Magalu', 'Porteiro Valdir', '-3 DAY', '6642', 'Apto 502 - Torre Norte', '-2 DAY'],
    ['Pacote', 'Mercado Livre', 'Porteiro Edson', '-5 DAY', '9013', 'Apto 304 - Torre Norte', '-5 DAY'],
];
foreach ($parcels as [$desc, $carrier, $by, $arrived, $code, $unit, $picked]) {
    $sql[] = insert('parcels', ['id' => $id('p'), 'condo_id' => CONDO, 'unit' => $unit, 'description' => $desc, 'carrier' => $carrier, 'received_by' => $by,
        'arrived_at' => at($arrived), 'pickup_code' => $code, 'status' => $picked ? 'PICKED_UP' : 'WAITING', 'picked_up_at' => $picked ? at($picked) : null,
        'delivered_by' => $picked ? $by : null]);
}

// Ocorrências: [título, descrição, categoria, situação, quando, apto, mensagens [autor, gestão?, texto, quando]]
$occurrences = [
    ['Vazamento no teto da garagem', 'Água pingando sobre a vaga 47 do 2º subsolo, perto do pilar G.', 'MAINTENANCE', 'IN_REVIEW', '-2 DAY', 'Apto 1003 - Torre Sul',
        [[$author, 1, 'Recebido. A empresa de impermeabilização vem avaliar na sexta pela manhã.', '-1 DAY']]],
    ['Barulho após as 22h', 'Som alto vindo de um apartamento do 7º andar na madrugada de sábado.', 'NOISE', 'RESOLVED', '-9 DAY', 'Apto 602 - Torre Norte',
        [[$author, 1, 'O morador foi notificado conforme o regimento interno.', '-8 DAY']]],
    ['Portão da garagem fechando devagar', 'O portão de saída está levando quase um minuto para fechar.', 'GARAGE', 'OPEN', '-5 HOUR', 'Apto 304 - Torre Norte', []],
    ['Lâmpada queimada no hall', 'Lâmpada do hall do 9º andar da Torre Sul queimada há dois dias.', 'MAINTENANCE', 'NEEDS_INFO', '-3 DAY', 'Apto 901 - Torre Sul',
        [[$author, 1, 'Qual lâmpada exatamente: a do elevador social ou a da escada?', '-2 DAY']]],
    ['Entregador sem identificação', 'Um entregador subiu até o andar sem ser anunciado pela portaria.', 'SECURITY', 'IN_REVIEW', '-1 DAY', 'Apto 1202 - Torre Norte', []],
];
foreach ($occurrences as [$title, $desc, $cat, $status, $when, $unit, $messages]) {
    $oid = $id('o');
    $sql[] = insert('occurrences', ['id' => $oid, 'condo_id' => CONDO, 'title' => $title, 'description' => $desc, 'category' => $cat, 'status' => $status,
        'created_at' => at($when), 'unit' => $unit, 'author_name' => $residentOf[$unit] ?? '', 'photo_ids' => '[]']);
    foreach ($messages as [$by, $mgmt, $text, $sent]) {
        $sql[] = insert('occurrence_messages', ['occurrence_id' => $oid, 'author' => $by, 'from_management' => $mgmt, 'text' => $text, 'sent_at' => at($sent), 'photo_ids' => '[]']);
    }
}

// Documentos (sem arquivo: o app gera o PDF de demonstração)
foreach ([['Regimento interno', 'BYLAWS', 400, 312], ['Convenção do condomínio', 'CONVENTION', 900, 540], ['Ata da AGO 2026 · prestação de contas', 'MINUTES', 40, 128], ['Ata da AGE · reforma da fachada', 'MINUTES', 120, 96]] as [$title, $cat, $daysAgo, $kb]) {
    $did = $id('doc');
    $sql[] = insert('condo_documents', ['id' => $did, 'condo_id' => CONDO, 'title' => $title, 'category' => $cat, 'file_id' => null, 'file_name' => "$did.pdf",
        'size_bytes' => $kb * 1024, 'uploaded_at' => at("-$daysAgo DAY"), 'uploaded_by' => $author]);
}

// Boletos de agosto a outubro (histórico). Outubro tem alguns em aberto (vencidos).
// Novembro entra pelo painel, com o PDF gerado abaixo.
$overdue = ['Apto 304 - Torre Norte', 'Apto 1002 - Torre Norte', 'Apto 501 - Torre Sul', 'Apto 803 - Torre Sul', 'Apto 1201 - Torre Sul'];
$seed = 0;
foreach ($units as $u) {
    foreach (['2026-08', '2026-09', '2026-10'] as $ref) {
        $due = "$ref-10";
        $cents = $fee($u);
        $open = $ref === '2026-10' && in_array($u[0], $overdue, true);
        $sql[] = insert('bills', ['id' => $id('b'), 'condo_id' => CONDO, 'unit' => $u[0], 'reference' => $ref, 'amount' => number_format($cents / 100, 2, '.', ''),
            'due_date' => $due, 'status' => $open ? 'OPEN' : 'PAID', 'barcode' => boleto_line($cents, $seed++, $due),
            'paid_at' => $open ? null : date('Y-m-d', strtotime("$due -" . mt_rand(0, 4) . ' days'))]);
    }
}
$sql[] = 'COMMIT;';
file_put_contents("$out/predio-demonstracao.sql", implode("\n", $sql) . "\n");

// ------------------------------------------------------------ remoção
// A maior parte cai em cascata com o condomínio; unit_documents/files e a administradora vão à mão.
$remove = [
    '-- Apaga o prédio de demonstração "Residencial Aurora" e a "Aurora Gestão Condominial".',
    '-- Os PDFs enviados pelo painel ficam órfãos no storage do servidor (só ocupam espaço).',
    'START TRANSACTION;',
    "DELETE FROM unit_documents WHERE condo_id = '" . CONDO . "';",
    "DELETE FROM reservations WHERE condo_id = '" . CONDO . "';",
    "DELETE FROM users WHERE company_id = '" . CO . "' OR condo_id = '" . CONDO . "';",
    "DELETE FROM condominiums WHERE id = '" . CONDO . "';",
    "DELETE FROM companies WHERE id = '" . CO . "';",
    'COMMIT;',
];
file_put_contents("$out/remover-predio-demonstracao.sql", implode("\n", $remove) . "\n");

// ------------------------------------------------------------ boletos de novembro (PDF)
$formats = [fn ($u) => "Unidade: {$u[0]}", fn ($u) => "Apto {$u[1]} - {$u[2]}", fn ($u) => "Unidade {$u[1]} {$u[2]}"];
$pages = [];
foreach ($units as $i => $u) {
    $cents = $fee($u);
    $unitText = $u[0] === 'Apto 1104 - Torre Sul' ? 'Unidade: nao informada' : $formats[$i % count($formats)]($u); // exceção de propósito
    $pages[] = [
        ['DEMONSTRACAO - BOLETO FICTICIO, NAO PAGUE', 9, true],
        ['Residencial Aurora', 14, true],
        'Beneficiario: Aurora Gestao Condominial',
        'Pagador: ' . ($residentOf[$u[0]] ?? 'Proprietario'),
        $unitText,
        'Referencia: novembro/2026',
        'Vencimento: 25/11/2026     Valor: R$ ' . number_format($cents / 100, 2, ',', '.'),
        '',
        ['Linha digitavel:', 10, true],
        [boleto_line($cents, 1000 + $i, '2026-11-25'), 12, false],
    ];
}
write_pdf("$out/aurora-boletos-novembro-2026.pdf", $pages);

printf("ok: %d apartamentos, %d cadastros, %d linhas de SQL em %s\n", count($units), count($people), count($sql), $out);
