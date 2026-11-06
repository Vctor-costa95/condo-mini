<?php
// Teste de fumaça contra uma API rodando: php scripts/smoke-test.php http://127.0.0.1:8080
// Usa as contas de teste (sem senha) e o morador demo "paulo.mendes" / condo123.
// Cria registros de verdade: rode só em banco de desenvolvimento.

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    exit;
}

$base = rtrim($argv[1] ?? 'http://127.0.0.1:8080', '/');
$failures = 0;

function call(string $method, string $path, ?array $body = null, ?string $token = null, ?array $multipart = null): array
{
    global $base;
    $headers = ['Accept: application/json'];
    if ($token) {
        $headers[] = "Authorization: Bearer $token";
    }
    $content = null;
    if ($multipart !== null) {
        $boundary = 'b' . bin2hex(random_bytes(8));
        $headers[] = "Content-Type: multipart/form-data; boundary=$boundary";
        $content = '';
        foreach ($multipart as $name => $value) {
            $content .= "--$boundary\r\n";
            if (is_array($value)) {
                $content .= "Content-Disposition: form-data; name=\"$name\"; filename=\"{$value['name']}\"\r\nContent-Type: {$value['type']}\r\n\r\n{$value['data']}\r\n";
            } else {
                $content .= "Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n";
            }
        }
        $content .= "--$boundary--\r\n";
    } elseif ($body !== null) {
        $headers[] = 'Content-Type: application/json';
        $content = json_encode($body);
    }
    $ctx = stream_context_create(['http' => [
        'method' => $method, 'header' => implode("\r\n", $headers), 'content' => $content, 'ignore_errors' => true,
    ]]);
    $raw = file_get_contents($base . $path, false, $ctx);
    preg_match('#^HTTP/\S+ (\d+)#', $http_response_header[0] ?? '', $m);
    return [(int) ($m[1] ?? 0), json_decode((string) $raw, true) ?? $raw];
}

function check(string $label, bool $ok, mixed $detail = null): void
{
    global $failures;
    echo ($ok ? '  ok   ' : '  FAIL ') . $label . ($ok ? '' : ' → ' . json_encode($detail, JSON_UNESCAPED_UNICODE)) . "\n";
    $failures += $ok ? 0 : 1;
}

function login(string $login, string $password = ''): string
{
    [$status, $body] = call('POST', '/auth/login', ['login' => $login, 'password' => $password]);
    check("login $login", $status === 200 && isset($body['token']), $body);
    return $body['token'] ?? '';
}

$tomorrow = date('Y-m-d', strtotime('+1 day'));
$farDay = date('Y-m-d', strtotime('+40 days'));

echo "Saúde e login\n";
check('GET /health', call('GET', '/health')[0] === 200);
check('senha errada recusada', call('POST', '/auth/login', ['login' => 'paulo.mendes', 'password' => 'x'])[0] === 401);
check('sem token → 401', call('GET', '/notices')[0] === 401);
$resident = login('morador');
$paulo = login('Paulo.Mendes', 'condo123');
$manager = login('sindico');
$doorman = login('porteiro');
$admin = login('admin');
[$s, $me] = call('GET', '/me', null, $resident);
check('morador tem dois apartamentos', $s === 200 && count($me['units']) === 2, $me);

echo "Avisos\n";
[$s, $list] = call('GET', '/notices', null, $resident);
check('morador lê avisos', $s === 200 && count($list) >= 5, $list);
check('morador não publica', call('POST', '/notices', ['title' => 'x', 'body' => 'y', 'category' => 'EVENT'], $resident)[0] === 403);
[$s, $notice] = call('POST', '/notices', ['title' => 'Teste', 'body' => 'Corpo', 'category' => 'EVENT', 'pinned' => true], $manager);
check('síndico publica', $s === 201 && $notice['author'] === 'Síndico - Carlos Andrade', $notice);
check('síndico exclui', call('DELETE', "/notices/{$notice['id']}", null, $manager)[0] === 200);

echo "Reservas\n";
[$s, $r] = call('POST', '/reservations', ['areaId' => 'a1', 'date' => $farDay, 'period' => 'MORNING', 'unit' => 'Apto 101 - Bloco A'], $resident);
check('reserva com taxa fica pendente', $s === 201 && $r['status'] === 'PENDING', $r);
[$s, $dup] = call('POST', '/reservations', ['areaId' => 'a1', 'date' => $farDay, 'period' => 'MORNING', 'unit' => 'Apto 104 - Bloco A'], $paulo);
check('mesmo horário recusado', $s === 409, $dup);
check('apto de outro recusado', call('POST', '/reservations', ['areaId' => 'a4', 'date' => $farDay, 'period' => 'NIGHT', 'unit' => 'Apto 302 - Bloco B'], $resident)[0] === 403);
[$s, $booked] = call('GET', "/areas/a1/booked?date=$farDay", null, $resident);
check('período aparece ocupado', $booked === ['MORNING'], $booked);
check('síndico aprova', call('POST', "/reservations/{$r['id']}/approve", null, $manager)[1]['status'] === 'CONFIRMED');
[$s, $all] = call('GET', '/reservations', null, $manager);
check('síndico vê todas', $s === 200 && count($all) >= 5, $all);
check('morador sem ?unit= recusado', call('GET', '/reservations', null, $resident)[0] === 403);
check('morador cancela', call('POST', "/reservations/{$r['id']}/cancel", null, $resident)[1]['status'] === 'CANCELED');
[$s, $closed] = call('PUT', '/areas/a4/closed', ['reason' => 'Pintura'], $manager);
check('interditar espaço', $closed['closedReason'] === 'Pintura', $closed);
check('reserva em espaço interditado recusada', call('POST', '/reservations', ['areaId' => 'a4', 'date' => $farDay, 'period' => 'NIGHT', 'unit' => 'Apto 101 - Bloco A'], $resident)[0] === 409);
call('PUT', '/areas/a4/closed', ['reason' => null], $manager);

echo "Reservas por hora\n";
check('horário invertido recusado', call('POST', '/areas', ['id' => 'a4', 'name' => 'Quadra poliesportiva', 'capacity' => 20, 'bookingMode' => 'HOURLY', 'opensAt' => 22, 'closesAt' => 8, 'maxHours' => 2], $manager)[0] === 422);
[$s, $court] = call('POST', '/areas', ['id' => 'a4', 'name' => 'Quadra poliesportiva', 'capacity' => 20, 'iconKey' => 'court', 'bookingMode' => 'HOURLY', 'opensAt' => 7, 'closesAt' => 22, 'maxHours' => 2], $manager);
check('quadra passa a ser por hora', $s === 200 && $court['bookingMode'] === 'HOURLY' && $court['opensAt'] === 7, $court);
$hourDay = date('Y-m-d', strtotime('+41 days'));
[$s, $h1] = call('POST', '/reservations', ['areaId' => 'a4', 'date' => $hourDay, 'startHour' => 14, 'endHour' => 16, 'unit' => 'Apto 101 - Bloco A'], $resident);
check('reserva das 14h às 16h', $s === 201 && $h1['period'] === null && $h1['startHour'] === 14 && $h1['endHour'] === 16 && $h1['status'] === 'CONFIRMED', $h1);
check('15h às 17h colide', call('POST', '/reservations', ['areaId' => 'a4', 'date' => $hourDay, 'startHour' => 15, 'endHour' => 17, 'unit' => 'Apto 104 - Bloco A'], $paulo)[0] === 409);
[$s, $h2] = call('POST', '/reservations', ['areaId' => 'a4', 'date' => $hourDay, 'startHour' => 16, 'endHour' => 18, 'unit' => 'Apto 104 - Bloco A'], $paulo);
check('16h às 18h, logo depois, passa', $s === 201, $h2);
check('3 horas seguidas recusadas', call('POST', '/reservations', ['areaId' => 'a4', 'date' => $hourDay, 'startHour' => 8, 'endHour' => 11, 'unit' => 'Apto 101 - Bloco A'], $resident)[0] === 422);
check('fora do funcionamento recusada', call('POST', '/reservations', ['areaId' => 'a4', 'date' => $hourDay, 'startHour' => 21, 'endHour' => 23, 'unit' => 'Apto 101 - Bloco A'], $resident)[0] === 422);
check('turno num espaço por hora recusado', call('POST', '/reservations', ['areaId' => 'a4', 'date' => $hourDay, 'period' => 'MORNING', 'unit' => 'Apto 101 - Bloco A'], $resident)[0] === 422);
[$s, $hours] = call('GET', "/areas/a4/booked-hours?date=$hourDay", null, $resident);
check('horas ocupadas: 14 a 17', $hours === [14, 15, 16, 17], $hours);
[$s, $periods] = call('GET', "/areas/a4/booked?date=$hourDay", null, $resident);
check('turno da tarde aparece ocupado pelas horas', $periods === ['AFTERNOON'], $periods);
call('POST', "/reservations/{$h1['id']}/cancel", null, $resident);
call('POST', "/reservations/{$h2['id']}/cancel", null, $paulo);
check('cancelar libera as horas', call('GET', "/areas/a4/booked-hours?date=$hourDay", null, $resident)[1] === []);

echo "Reservas do dia inteiro\n";
$hall = ['id' => 'a1', 'name' => 'Salão de festas', 'description' => 'Salão climatizado com cozinha equipada e banheiros.', 'capacity' => 80, 'fee' => 150, 'rules' => 'Limpeza por conta do morador. Som até 22h.', 'iconKey' => 'party'];
[$s, $daily] = call('POST', '/areas', $hall + ['bookingMode' => 'DAILY'], $manager);
check('salão passa a ser do dia inteiro', $s === 200 && $daily['bookingMode'] === 'DAILY', $daily);
$dayDay = date('Y-m-d', strtotime('+43 days'));
[$s, $d1] = call('POST', '/reservations', ['areaId' => 'a1', 'date' => $dayDay, 'unit' => 'Apto 101 - Bloco A'], $resident);
check('reserva do dia inteiro, pendente', $s === 201 && $d1['period'] === null && $d1['startHour'] === 0 && $d1['endHour'] === 24 && $d1['status'] === 'PENDING', $d1);
check('mesmo dia recusado enquanto pendente', call('POST', '/reservations', ['areaId' => 'a1', 'date' => $dayDay, 'unit' => 'Apto 104 - Bloco A'], $paulo)[0] === 409);
check('dia todo aparece ocupado', call('GET', "/areas/a1/booked-hours?date=$dayDay", null, $resident)[1] === range(0, 23));
check('síndico recusa', call('POST', "/reservations/{$d1['id']}/reject", ['reason' => 'Teste'], $manager)[1]['status'] === 'REJECTED');
[$s, $d2] = call('POST', '/reservations', ['areaId' => 'a1', 'date' => $dayDay, 'unit' => 'Apto 104 - Bloco A'], $paulo);
check('recusada libera o dia', $s === 201, $d2);
check('recusada não pode ser aprovada depois', call('POST', "/reservations/{$d1['id']}/approve", null, $manager)[0] === 409);
call('POST', "/reservations/{$d2['id']}/cancel", null, $paulo);
check('cancelada não pode ser aprovada', call('POST', "/reservations/{$d2['id']}/approve", null, $manager)[0] === 409);
call('POST', '/areas', $hall + ['bookingMode' => 'PERIOD'], $manager);

echo "Mudanças e reparos\n";
[$s, $move] = call('POST', '/service-bookings', ['type' => 'MOVE', 'date' => $farDay, 'period' => 'AFTERNOON', 'unit' => 'Apto 101 - Bloco A', 'provider' => 'Mudanças X'], $resident);
check('pedido de mudança', $s === 201 && $move['status'] === 'PENDING', $move);
check('segunda mudança no período recusada', call('POST', '/service-bookings', ['type' => 'MOVE', 'date' => $farDay, 'period' => 'AFTERNOON', 'unit' => 'Apto 104 - Bloco A'], $paulo)[0] === 409);
check('noite recusada', call('POST', '/service-bookings', ['type' => 'REPAIR', 'date' => $farDay, 'period' => 'NIGHT', 'unit' => 'Apto 101 - Bloco A'], $resident)[0] === 422);
[$s, $taken] = call('GET', "/service-bookings/taken-moves?date=$farDay", null, $resident);
check('horário de mudança ocupado', $taken === ['AFTERNOON'], $taken);
[$s, $rej] = call('POST', "/service-bookings/{$move['id']}/reject", ['reason' => 'Elevador em obra'], $manager);
check('síndico recusa com motivo', $rej['status'] === 'REJECTED' && $rej['rejectionReason'] === 'Elevador em obra', $rej);
[$s, $agenda] = call('GET', '/service-bookings', null, $doorman);
check('portaria vê agenda', $s === 200 && count($agenda) >= 5, $agenda);
check('pedido recusado não pode ser aprovado', call('POST', "/service-bookings/{$move['id']}/approve", null, $manager)[0] === 409);

echo "Histórico\n";
[, $fix] = call('POST', '/service-bookings', ['type' => 'REPAIR', 'repairKind' => 'ELECTRICAL', 'date' => $farDay, 'period' => 'MORNING', 'unit' => 'Apto 101 - Bloco A'], $resident);
call('POST', "/service-bookings/{$fix['id']}/approve", null, $manager);
call('POST', "/service-bookings/{$fix['id']}/cancel", ['reason' => 'Obra na rede'], $manager);
check('pedido cancelado não pode ser aprovado', call('POST', "/service-bookings/{$fix['id']}/approve", null, $manager)[0] === 409);
$unitQuery = 'unit=' . rawurlencode('Apto 101 - Bloco A');
[$s, $history] = call('GET', "/reservations?history=1&$unitQuery", null, $resident);
$ids = array_column($history, 'id');
check('histórico do morador traz a reserva que ele cancelou', $s === 200 && in_array($r['id'], $ids, true), $ids);
$dates = array_column($history, 'date');
$sorted = $dates;
rsort($sorted);
check('histórico vem do mais recente para o mais antigo', $dates === $sorted, $dates);
[, $active] = call('GET', "/reservations?$unitQuery", null, $resident);
check('lista normal continua sem a reserva cancelada pelo morador', !in_array($r['id'], array_column($active, 'id'), true));
[$s, $servicesHistory] = call('GET', "/service-bookings?history=1&$unitQuery", null, $resident);
$byId = array_column($servicesHistory, null, 'id');
check('histórico traz pedido recusado e cancelado pelo síndico, com motivo', $s === 200
    && ($byId[$move['id']]['status'] ?? '') === 'REJECTED'
    && ($byId[$fix['id']]['rejectionReason'] ?? '') === 'Obra na rede', array_keys($byId));
check('morador sem apartamento não vê histórico do condomínio', call('GET', '/reservations?history=1', null, $resident)[0] === 403);
check('portaria não vê histórico do condomínio', call('GET', '/service-bookings?history=1', null, $doorman)[0] === 403);
[$s, $condoHistory] = call('GET', '/reservations?history=1', null, $manager);
check('síndico vê o histórico do condomínio todo', $s === 200 && in_array($r['id'], array_column($condoHistory, 'id'), true)
    && count(array_unique(array_column($condoHistory, 'unit'))) > 1, count($condoHistory));

echo "Boletos\n";
[$s, $bills] = call('GET', '/bills?unit=' . rawurlencode('Apto 101 - Bloco A'), null, $resident);
check('8 boletos', $s === 200 && count($bills) === 8, $bills);
check('vencido calculado', in_array('OVERDUE', array_column($bills, 'status'), true) || date('j') < 10, array_column($bills, 'status'));
check('boleto de outro apto recusado', call('GET', '/bills?unit=' . rawurlencode('Apto 302 - Bloco B'), null, $resident)[0] === 403);
check('sem PDF → 404', call('GET', "/bills/{$bills[0]['id']}/pdf", null, $resident)[0] === 404);
$pdf = ['name' => 'boleto.pdf', 'type' => 'application/pdf', 'data' => "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"];
[$s, $doc] = call('POST', '/unit-documents', null, $admin, ['condoId' => 'c1', 'unit' => 'Apto 101 - Bloco A', 'reference' => $bills[0]['reference'], 'file' => $pdf]);
check('admin envia PDF do boleto', $s === 201, $doc);
[$s] = call('GET', "/bills/{$bills[0]['id']}/pdf", null, $resident);
check('morador baixa o PDF', $s === 200);
check('morador de outro apto não baixa o arquivo', call('GET', '/files/' . basename($doc['url'] ?? 'x'), null, $paulo)[0] === 404);
check('admin exclui PDF', call('DELETE', "/unit-documents/{$doc['id']}", null, $admin)[0] === 200);

echo "Encomendas\n";
$png = ['name' => 'foto.png', 'type' => 'image/png', 'data' => base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=')];
[$s, $photo] = call('POST', '/files', null, $doorman, ['file' => $png]);
check('portaria envia foto', $s === 201, $photo);
[$s, $parcel] = call('POST', '/parcels', ['unit' => 'Apto 101 - Bloco A', 'description' => 'Caixa', 'carrier' => 'Correios', 'photoUrl' => $photo['url'] ?? ''], $doorman);
check('portaria registra sem ver o código', $s === 201 && $parcel['pickupCode'] === '' && $parcel['receivedBy'] === 'Porteiro José', $parcel);
[$s, $mine] = call('GET', '/parcels?unit=' . rawurlencode('Apto 101 - Bloco A'), null, $resident);
$code = array_values(array_filter($mine, fn ($p) => $p['id'] === $parcel['id']))[0]['pickupCode'] ?? '';
check('morador vê o código', strlen($code) === 4, $mine);
check('código errado recusado', call('POST', "/parcels/{$parcel['id']}/deliver", ['code' => '0000'], $doorman)[0] === 422);
[$s, $delivered] = call('POST', "/parcels/{$parcel['id']}/deliver", ['code' => $code], $doorman);
check('entrega com código', $s === 200 && $delivered['status'] === 'PICKED_UP', $delivered);
check('foto acessível ao morador', call('GET', '/files/' . $photo['id'], null, $resident)[0] === 200);

echo "Ocorrências\n";
[$s, $occ] = call('POST', '/occurrences', ['title' => 'Teste', 'description' => 'Descrição', 'category' => 'NOISE', 'unit' => 'Apto 101 - Bloco A', 'photoUrls' => [$photo['url']]], $resident);
check('morador registra com foto', $s === 201 && str_starts_with($occ['protocol'], 'OC-') && count($occ['photoUrls']) === 1, $occ);
[$s, $occ] = call('POST', "/occurrences/{$occ['id']}/request-details", ['text' => 'Qual horário?'], $manager);
check('síndico pede detalhes', $occ['status'] === 'NEEDS_INFO', $occ);
[$s, $occ] = call('POST', "/occurrences/{$occ['id']}/reply", ['text' => 'Às 23h'], $resident);
check('morador responde', $occ['status'] === 'IN_REVIEW' && count($occ['messages']) === 2, $occ);
[$s, $occ] = call('POST', "/occurrences/{$occ['id']}/respond", ['text' => 'Notificado', 'resolve' => true], $manager);
check('síndico resolve', $occ['status'] === 'RESOLVED', $occ);

echo "Documentos\n";
[$s, $docs] = call('GET', '/documents', null, $resident);
check('morador lê documentos', $s === 200 && count($docs) === 4, $docs);
[$s, $doc] = call('POST', '/documents', null, $manager, ['title' => 'Ata teste', 'category' => 'MINUTES', 'file' => $pdf]);
check('síndico publica PDF', $s === 201 && $doc['url'] !== '', $doc);
check('foto não vale como PDF', call('POST', '/documents', null, $manager, ['title' => 'x', 'category' => 'OTHER', 'file' => $png])[0] === 415);
check('síndico exclui', call('DELETE', "/documents/{$doc['id']}", null, $manager)[0] === 200);

echo "Cadastro pelo próprio morador\n";
[$s, $publicCondos] = call('GET', '/public/condos');
$jardins = array_values(array_filter($publicCondos, fn ($c) => $c['id'] === 'c1'))[0] ?? null;
check('prédios sem login, sem moradores', $s === 200 && $jardins !== null && !isset($jardins['users']), $publicCondos);
$suffix = substr(bin2hex(random_bytes(3)), 0, 6);
$signup = fn (string $login, array $extra = []) => call('POST', '/signup', $extra + [
    'condoId' => 'c1', 'name' => 'Pedido Teste', 'email' => 'pedido@email.com', 'phone' => '(11) 90000-0000',
    'login' => $login, 'password' => 'segredo1', 'unit' => 'Apto 1301 - Bloco A',
]);
check('apartamento inexistente recusado', $signup("pt.$suffix", ['unit' => 'Apto 9999 - Bloco A'])[0] === 422);
check('senha curta recusada', $signup("pt.$suffix", ['password' => '123'])[0] === 422);
check('usuário já usado recusado', $signup('morador')[0] === 409);
[$s, $req] = $signup("pt.$suffix");
check('pedido criado', $s === 201 && $req['status'] === 'PENDING', $req);
check('mesmo usuário em outro pedido recusado', $signup("pt.$suffix")[0] === 409);
[$s, $err] = call('POST', '/auth/login', ['login' => "pt.$suffix", 'password' => 'segredo1']);
check('login pendente explica a espera', $s === 403 && str_contains($err['error'], 'aguardando'), $err);
check('senha errada no pedido continua genérica', call('POST', '/auth/login', ['login' => "pt.$suffix", 'password' => 'errada'])[0] === 401);
check('síndico não vê pedidos', call('GET', '/signup-requests?condoId=c1', null, $manager)[0] === 403);
[$s, $pending] = call('GET', '/signup-requests?condoId=c1', null, $admin);
check('admin vê o pedido', $s === 200 && in_array($req['id'], array_column($pending, 'id'), true), $pending);
check('admin não cria usuário com login de pedido pendente', call('GET', "/logins/available?login=pt.$suffix", null, $admin)[1]['available'] === false);
[$s, $approved] = call('POST', "/signup-requests/{$req['id']}/approve", ['unit' => 'Apto 1302 - Bloco A'], $admin);
check('admin aceita trocando o apartamento', $s === 200 && $approved['role'] === 'RESIDENT' && $approved['units'] === ['Apto 1302 - Bloco A'], $approved);
check('pedido aceito não aceita de novo', call('POST', "/signup-requests/{$req['id']}/approve", null, $admin)[0] === 404);
$newResident = login("pt.$suffix", 'segredo1');
[$s, $meNew] = call('GET', '/me', null, $newResident);
check('morador novo entra com a própria senha', $s === 200 && $meNew['unit'] === 'Apto 1302 - Bloco A', $meNew);
call('DELETE', "/users/{$approved['id']}", null, $admin);
[, $req2] = $signup("pr.$suffix");
check('recusa exige motivo', call('POST', "/signup-requests/{$req2['id']}/reject", null, $admin)[0] === 422);
call('POST', "/signup-requests/{$req2['id']}/reject", ['reason' => 'Apartamento sem morador novo'], $admin);
[$s, $err] = call('POST', '/auth/login', ['login' => "pr.$suffix", 'password' => 'segredo1']);
check('login recusado mostra o motivo', $s === 403 && str_contains($err['error'], 'Apartamento sem morador novo'), $err);
[$s, $again] = $signup("pr.$suffix");
check('pedido recusado pode ser refeito', $s === 201, $again);
$flood = [$again['id'] ?? null];
$limited = false;
for ($i = 0; $i < 6 && !$limited; $i++) {
    [$s, $made] = $signup("pf$i.$suffix");
    $limited = $s === 429;
    if ($s === 201) $flood[] = $made['id'];
}
check('limite de pedidos pendentes por endereço', $limited);
foreach (array_filter($flood) as $id) {
    call('POST', "/signup-requests/$id/reject", ['reason' => 'Teste automático'], $admin);
}

echo "Cadastro (administração)\n";
[$s, $condos] = call('GET', '/condos', null, $admin);
check('admin vê 3 condomínios', $s === 200 && count($condos) === 3, $condos);
check('síndico vê só o dele', count(call('GET', '/condos', null, $manager)[1]) === 1);
check('admin precisa escolher condomínio', call('GET', '/notices', null, $admin)[0] === 422);
check('admin lê avisos do c1', call('GET', '/notices?condoId=c1', null, $admin)[0] === 200);
[$s, $u] = call('POST', '/users', ['condoId' => 'c1', 'name' => 'Teste Novo', 'role' => 'RESIDENT', 'login' => 'teste.novo', 'password' => 'segredo1', 'units' => ['Apto 102 - Bloco A']], $admin);
check('admin cria morador', $s === 200 && $u['units'] === ['Apto 102 - Bloco A'], $u);
check('login duplicado recusado', call('POST', '/users', ['condoId' => 'c1', 'name' => 'X', 'role' => 'RESIDENT', 'login' => 'Teste.Novo', 'password' => 'segredo1'], $admin)[0] === 409);
login('teste.novo', 'segredo1');
[$s, $u] = call('POST', "/users/{$u['id']}/units", ['unit' => 'Apto 103 - Bloco A'], $admin);
check('liga mais um apto', count($u['units']) === 2, $u);
check('admin exclui', call('DELETE', "/users/{$u['id']}", null, $admin)[0] === 200);
check('síndico não cria cadastro', call('POST', '/users', ['name' => 'X', 'role' => 'RESIDENT', 'login' => 'x.y', 'password' => 'segredo1'], $manager)[0] === 403);
$c1 = array_values(array_filter($condos, fn ($c) => $c['id'] === 'c1'))[0];
[$s, $saved] = call('POST', '/condos', $c1, $admin);
check('salvar condomínio mantém blocos', $s === 200 && count($saved['towers']) === 3, $saved);
[$s, $me] = call('GET', '/me', null, $resident);
check('aptos do morador continuam', count($me['units']) === 2, $me);

// Com 'push' => ['log_file' => ...] no config.php, as notificações vão para o arquivo
// passado como segundo argumento em vez do Firebase.
$pushLog = $argv[2] ?? null;
if ($pushLog !== null) {
    echo "Notificações\n";
    @unlink($pushLog);
    $pushed = function () use ($pushLog): array {
        usleep(200_000);
        $lines = is_file($pushLog) ? file($pushLog, FILE_IGNORE_NEW_LINES) : [];
        @unlink($pushLog);
        return array_map(fn ($l) => json_decode($l, true), $lines);
    };
    check('aparelho do morador registrado', call('POST', '/devices', ['token' => 'test-ana-device', 'platform' => 'android'], $resident)[0] === 200);
    check('aparelho do síndico registrado', call('POST', '/devices', ['token' => 'test-carlos-device'], $manager)[0] === 200);
    check('token inválido recusado', call('POST', '/devices', ['token' => 'bad token!'], $resident)[0] === 422);

    call('POST', '/parcels', ['unit' => 'Apto 101 - Bloco A', 'description' => 'Caixa pequena'], $doorman);
    $sent = $pushed();
    check('encomenda avisa o morador, sem o código', count($sent) === 1 && $sent[0]['token'] === 'test-ana-device'
        && $sent[0]['data']['tab'] === 'parcels' && !preg_match('/\d{4}/', $sent[0]['notification']['body']), $sent);

    call('POST', '/notices', ['title' => 'Aviso push', 'body' => 'Corpo', 'category' => 'EVENT'], $manager);
    $sent = $pushed();
    check('aviso vai para o morador e não para quem publicou', array_column($sent, 'token') === ['test-ana-device'], $sent);

    [, $r] = call('POST', '/reservations', ['areaId' => 'a2', 'date' => $farDay, 'period' => 'NIGHT', 'unit' => 'Apto 101 - Bloco A'], $resident);
    $sent = $pushed();
    check('reserva pendente avisa o síndico', array_column($sent, 'token') === ['test-carlos-device'] && $sent[0]['notification']['title'] === 'Reserva para aprovar', $sent);
    call('POST', "/reservations/{$r['id']}/reject", ['reason' => 'Manutenção'], $manager);
    $sent = $pushed();
    check('recusa avisa o morador com o motivo', count($sent) === 1 && str_contains($sent[0]['notification']['body'], 'Motivo: Manutenção'), $sent);

    [, $b] = call('POST', '/service-bookings', ['type' => 'REPAIR', 'repairKind' => 'INTERNET', 'date' => $farDay, 'period' => 'MORNING', 'unit' => 'Apto 101 - Bloco A'], $resident);
    $pushed();
    call('POST', "/service-bookings/{$b['id']}/approve", null, $manager);
    $sent = $pushed();
    check('reparo aprovado avisa o morador', ($sent[0]['notification']['title'] ?? '') === 'Reparo aprovado', $sent);
    check('síndico não cancela pedido sem motivo', call('POST', "/service-bookings/{$b['id']}/cancel", null, $manager)[0] === 422);
    [$s, $canceledBooking] = call('POST', "/service-bookings/{$b['id']}/cancel", ['reason' => 'Falta de luz no prédio'], $manager);
    $sent = $pushed();
    check('síndico cancela reparo aprovado', $s === 200 && $canceledBooking['status'] === 'CANCELED'
        && $canceledBooking['rejectionReason'] === 'Falta de luz no prédio', $canceledBooking);
    check('cancelamento do reparo avisa o morador com o motivo', count($sent) === 1 && $sent[0]['token'] === 'test-ana-device'
        && $sent[0]['notification']['title'] === 'Reparo cancelado pelo síndico'
        && str_contains($sent[0]['notification']['body'], 'Motivo: Falta de luz no prédio'), $sent);

    // Emergência: o síndico cancela uma reserva já confirmada.
    [, $c] = call('POST', '/reservations', ['areaId' => 'a3', 'date' => $farDay, 'period' => 'AFTERNOON', 'unit' => 'Apto 101 - Bloco A'], $resident);
    if ($c['status'] === 'PENDING') {
        $pushed();
        call('POST', "/reservations/{$c['id']}/approve", null, $manager);
    }
    $pushed();
    check('síndico não cancela reserva sem motivo', call('POST', "/reservations/{$c['id']}/cancel", null, $manager)[0] === 422);
    [$s, $canceled] = call('POST', "/reservations/{$c['id']}/cancel", ['reason' => 'Vazamento no salão'], $manager);
    $sent = $pushed();
    check('síndico cancela reserva confirmada', $s === 200 && $canceled['status'] === 'CANCELED'
        && $canceled['rejectionReason'] === 'Vazamento no salão', $canceled);
    check('cancelamento avisa o morador com o motivo', count($sent) === 1 && $sent[0]['token'] === 'test-ana-device'
        && $sent[0]['notification']['title'] === 'Reserva cancelada pelo síndico'
        && str_contains($sent[0]['notification']['body'], 'Motivo: Vazamento no salão'), $sent);
    check('reserva já cancelada não cancela de novo', call('POST', "/reservations/{$c['id']}/cancel", ['reason' => 'x'], $manager)[0] === 409);
    [$s, $mine] = call('GET', '/reservations?unit=' . rawurlencode('Apto 101 - Bloco A'), null, $resident);
    $seen = array_values(array_filter($mine, fn ($x) => $x['id'] === $c['id']));
    check('morador vê a reserva cancelada com o motivo', $s === 200 && ($seen[0]['rejectionReason'] ?? '') === 'Vazamento no salão', $seen);

    [, $o] = call('POST', '/occurrences', ['title' => 'Push', 'description' => 'Teste', 'category' => 'OTHER', 'unit' => 'Apto 101 - Bloco A'], $resident);
    $sent = $pushed();
    check('ocorrência nova avisa o síndico', array_column($sent, 'token') === ['test-carlos-device'], $sent);
    call('POST', "/occurrences/{$o['id']}/respond", ['text' => 'Resolvido', 'resolve' => true], $manager);
    $sent = $pushed();
    check('resposta avisa o morador', str_contains($sent[0]['notification']['title'] ?? '', 'resolvida'), $sent);

    $morador = login('morador');
    call('POST', '/devices', ['token' => 'test-ana-device'], $morador);
    call('POST', '/auth/logout', null, $morador);
    call('POST', '/notices', ['title' => 'Depois do logout', 'body' => 'x', 'category' => 'EVENT'], $manager);
    check('sair da conta para os envios ao aparelho', $pushed() === []);
}

echo "Sessão\n";
check('logout', call('POST', '/auth/logout', null, $paulo)[0] === 200);
check('token antigo recusado', call('GET', '/me', null, $paulo)[0] === 401);

echo $failures === 0 ? "\nTudo certo.\n" : "\n$failures falha(s).\n";
exit($failures === 0 ? 0 : 1);
