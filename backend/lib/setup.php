<?php
// Cria as tabelas e, num banco vazio, os dados de demonstração (os mesmos dos repositórios Fake do app).

declare(strict_types=1);

function install_schema(): void
{
    $sql = file_get_contents(dirname(__DIR__) . '/sql/schema.sql');
    // Sem ";" dentro dos comandos, então dá para separar assim.
    foreach (array_filter(array_map('trim', explode(';', preg_replace('/^--.*$/m', '', $sql)))) as $statement) {
        db()->exec($statement);
    }
}

function database_is_empty(): bool
{
    return (int) row('SELECT COUNT(*) AS n FROM condominiums')['n'] === 0;
}

function seed_demo(): void
{
    $now = new DateTimeImmutable();
    $at = fn (string $modify) => $now->modify($modify)->format('Y-m-d H:i:s');
    $day = fn (int $offset) => $now->modify("$offset days")->format('Y-m-d');
    $insert = function (string $table, array $values): void {
        $cols = array_keys($values);
        exec_sql(
            "INSERT INTO $table (" . implode(', ', $cols) . ') VALUES (' . implode(', ', array_map(fn ($c) => ":$c", $cols)) . ')',
            $values
        );
    };

    // ------------------------------------------------------------ condomínios
    $condos = [
        ['c1', 'Residencial Jardins', 'Rua Oscar Freire, 1200 · Jardins, São Paulo', [
            ['c1-a', 'Bloco A', 14, 4, ['14' => 2]],
            ['c1-b', 'Bloco B', 12, 6, ['0' => 2]],
            ['c1-c', 'Bloco C', 10, 2, []],
        ]],
        ['c2', 'Edifício Vista Mar', 'Av. Atlântica, 3400 · Copacabana, Rio de Janeiro', [
            ['c2-t', 'Torre única', 20, 2, ['1' => 0, '20' => 1]],
        ]],
        ['c3', 'Parque das Águas', 'Alameda das Palmeiras, 88 · Alphaville, Barueri', array_map(
            fn ($i) => ["c3-t$i", "Torre $i", 8, 10, []],
            range(1, 4)
        )],
    ];
    foreach ($condos as [$id, $name, $address, $towers]) {
        $insert('condominiums', ['id' => $id, 'name' => $name, 'address' => $address]);
        foreach ($towers as $pos => [$tid, $tname, $floors, $perFloor, $overrides]) {
            $insert('towers', [
                'id' => $tid, 'condo_id' => $id, 'name' => $tname, 'floors' => $floors,
                'units_per_floor' => $perFloor, 'floor_overrides' => json_encode((object) $overrides), 'position' => $pos,
            ]);
        }
    }

    // ------------------------------------------------------------ pessoas
    // Um cadastro de cada perfil é conta de teste, sem senha, com o nome do perfil
    // como usuário; os demais entram com "nome.sobrenome" e a senha condo123.
    $testLogins = ['u1' => 'morador', 'u11' => 'sindico', 'u12' => 'porteiro', 'u99' => 'admin'];
    $demoHash = password_hash('condo123', PASSWORD_DEFAULT);
    $people = [
        ['u1', 'Ana Ribeiro', 'RESIDENT', 'c1', ['Apto 101 - Bloco A', 'Apto 1402 - Bloco A']],
        ['u2', 'Paulo Mendes', 'RESIDENT', 'c1', ['Apto 104 - Bloco A']],
        ['u3', 'Clara Mendes', 'RESIDENT', 'c1', ['Apto 104 - Bloco A']],
        ['u4', 'Marina Costa', 'RESIDENT', 'c1', ['Apto 302 - Bloco B']],
        ['u5', 'Ricardo Alves', 'RESIDENT', 'c1', ['Apto 503 - Bloco B']],
        ['u6', 'Helena Rocha', 'RESIDENT', 'c1', ['Apto 201 - Bloco C']],
        ['u7', 'Fernanda Lopes', 'RESIDENT', 'c1', ['Apto 203 - Bloco A']],
        ['u8', 'Gustavo Prado', 'RESIDENT', 'c1', ['Apto 402 - Bloco C']],
        ['u9', 'Beatriz Nunes', 'RESIDENT', 'c1', []],
        ['u10', 'Lucas Ferreira', 'RESIDENT', 'c1', []],
        ['u11', 'Carlos Andrade', 'MANAGER', 'c1', ['Apto 401 - Bloco A'], 'carlos.andrade@email.com', '(11) 98811-2040'],
        ['u12', 'José Silva', 'DOORMAN', 'c1', [], 'portaria.jardins@condomini.app', '(11) 3030-1000'],
        ['u13', 'Antônio Lima', 'DOORMAN', 'c1', [], 'portaria.jardins@condomini.app', '(11) 3030-1000'],
        ['u20', 'Sofia Bragança', 'RESIDENT', 'c2', ['Apto 2001 - Torre única']],
        ['u21', 'Henrique Vasconcelos', 'RESIDENT', 'c2', ['Apto 1502 - Torre única']],
        ['u22', 'Marcelo Tavares', 'MANAGER', 'c2', ['Apto 801 - Torre única'], 'marcelo.tavares@email.com', '(21) 99820-1144'],
        ['u30', 'Juliana Matos', 'RESIDENT', 'c3', ['Apto 507 - Torre 2']],
        ['u31', 'Rafael Couto', 'RESIDENT', 'c3', ['Apto 110 - Torre 4']],
        ['u32', 'Camila Duarte', 'RESIDENT', 'c3', []],
        ['u99', 'Renata Prime', 'ADMIN', null, [], 'renata@primeadm.com.br', '(11) 97700-1122'],
    ];
    foreach ($people as $p) {
        [$id, $name, $role, $condoId, $units] = $p;
        $parts = explode(' ', $name);
        $login = $testLogins[$id] ?? login_key($parts[0] . '.' . end($parts));
        $insert('users', [
            'id' => $id,
            'condo_id' => $condoId,
            'name' => $name,
            'role' => $role,
            'email' => $p[5] ?? str_replace(' ', '.', login_key($name)) . '@email.com',
            'phone' => $p[6] ?? '(11) 9' . str_pad(substr($id, 1), 4, '0', STR_PAD_LEFT) . '-0000',
            'login' => $login,
            'login_key' => login_key($login),
            'password_hash' => isset($testLogins[$id]) ? null : $demoHash,
        ]);
        set_user_units($id, $units);
    }

    // ------------------------------------------------------------ avisos
    $notices = [
        ['n1', 'Assembleia geral ordinária', 'Convocamos todos os condôminos para a assembleia geral no dia 10, às 19h30, no salão de festas. Pauta: prestação de contas, previsão orçamentária e eleição do conselho fiscal.', 'MANAGER', 'Síndico - Carlos Andrade', $at('-3 hours'), 1],
        ['n2', 'Interrupção no fornecimento de água', "Na próxima quinta-feira, das 9h às 14h, o fornecimento de água será interrompido para limpeza das caixas d'água. Reserve água com antecedência.", 'IMPORTANT', 'Administração', $at('-1 day'), 1],
        ['n3', 'Manutenção do elevador social', 'O elevador social do Bloco B passará por manutenção preventiva na segunda-feira, das 8h às 12h. Utilize o elevador de serviço.', 'MAINTENANCE', 'Síndico - Carlos Andrade', $at('-2 days'), 0],
        ['n4', 'Festa junina do condomínio', 'Venha participar da nossa festa junina no dia 22, a partir das 17h, na área da churrasqueira. Traga um prato típico!', 'EVENT', 'Comissão de eventos', $at('-5 days'), 0],
        ['n5', 'Novo horário da coleta seletiva', 'A partir deste mês, a coleta de recicláveis acontece às terças e sextas. Separe o lixo corretamente e deposite nos contêineres identificados.', 'MANAGER', 'Síndico - Carlos Andrade', $at('-9 days'), 0],
    ];
    foreach ($notices as [$id, $title, $body, $category, $author, $publishedAt, $pinned]) {
        $insert('notices', [
            'id' => $id, 'condo_id' => 'c1', 'title' => $title, 'body' => $body, 'category' => $category,
            'author' => $author, 'published_at' => $publishedAt, 'pinned' => $pinned,
        ]);
    }

    // ------------------------------------------------------------ espaços e reservas
    $areas = [
        ['a1', 'c1', 'Salão de festas', 'Salão climatizado com cozinha equipada e banheiros.', 80, 150, 'Limpeza por conta do morador. Som até 22h.', 'party'],
        ['a2', 'c1', 'Churrasqueira', 'Área coberta com churrasqueira, pia e mesas.', 30, 80, 'Trazer carvão. Devolver a área limpa.', 'grill'],
        ['a3', 'c1', 'Espaço gourmet', 'Cozinha gourmet integrada com mesa para 12 pessoas.', 12, 100, 'Proibido uso de som alto.', 'gourmet'],
        ['a4', 'c1', 'Quadra poliesportiva', 'Quadra coberta para futebol, vôlei e basquete.', 20, 0, 'Uso de calçado adequado obrigatório.', 'court'],
        ['a5', 'c1', 'Sala de reuniões', 'Sala com TV, mesa e 8 cadeiras.', 8, 0, 'Reserva máxima de um período por dia.', 'meeting'],
        ['a6', 'c2', 'Rooftop com piscina', 'Piscina com borda infinita e vista para o mar.', 25, 0, 'Uso até 22h. Crianças acompanhadas.', 'pool'],
        ['a7', 'c2', 'Academia', 'Equipamentos Technogym e sala de pilates.', 12, 0, 'Uso de toalha obrigatório.', 'gym'],
        ['a8', 'c3', 'Salão de festas', 'Salão para 120 pessoas com copa.', 120, 250, 'Som até 23h.', 'party'],
        ['a9', 'c3', 'Quadra de tênis', 'Quadra de saibro com iluminação.', 4, 0, 'Reserva de um período por dia.', 'tennis'],
        ['a10', 'c3', 'Brinquedoteca', 'Espaço infantil com monitoria aos sábados.', 15, 0, 'Crianças até 10 anos.', 'kids'],
    ];
    foreach ($areas as [$id, $condoId, $name, $description, $capacity, $fee, $rules, $icon]) {
        $insert('common_areas', [
            'id' => $id, 'condo_id' => $condoId, 'name' => $name, 'description' => $description,
            'capacity' => $capacity, 'fee' => $fee, 'rules' => $rules, 'icon_key' => $icon,
        ]);
    }
    $reservations = [
        ['r1', 'a1', 3, 'NIGHT', 'Apto 302 - Bloco B', 'CONFIRMED', 'Marina Costa'],
        ['r2', 'a2', 2, 'AFTERNOON', 'Apto 104 - Bloco A', 'CONFIRMED', 'Paulo Mendes'],
        ['r3', 'a1', 7, 'AFTERNOON', 'Apto 201 - Bloco C', 'PENDING', 'Helena Rocha'],
        ['r4', 'a3', 5, 'NIGHT', 'Apto 503 - Bloco B', 'PENDING', 'Ricardo Alves'],
    ];
    foreach ($reservations as [$id, $areaId, $offset, $period, $unit, $status, $resident]) {
        $insert('reservations', [
            'id' => $id, 'condo_id' => 'c1', 'area_id' => $areaId, 'date' => $day($offset), 'period' => $period,
            'unit' => $unit, 'status' => $status, 'resident_name' => $resident, 'created_at' => $at('-1 day'),
        ]);
    }

    // ------------------------------------------------------------ mudanças e reparos
    $bookings = [
        ['s1', 'MOVE', 2, 'MORNING', 'Apto 302 - Bloco B', 'Marina Costa', 'CONFIRMED', null, ''],
        ['s2', 'MOVE', 4, 'AFTERNOON', 'Apto 203 - Bloco A', 'Fernanda Lopes', 'PENDING', null, 'Mudanças Rápidas'],
        ['s3', 'REPAIR', 1, 'AFTERNOON', 'Apto 503 - Bloco B', 'Ricardo Alves', 'PENDING', 'INTERNET', 'Vivo Fibra'],
        ['s4', 'REPAIR', 0, 'MORNING', 'Apto 402 - Bloco C', 'Gustavo Prado', 'CONFIRMED', 'ELECTRICAL', 'Eletricista Marcos'],
        ['s5', 'MOVE', 0, 'AFTERNOON', 'Apto 201 - Bloco C', 'Helena Rocha', 'CONFIRMED', null, 'Transportes Ideal'],
    ];
    foreach ($bookings as [$id, $type, $offset, $period, $unit, $resident, $status, $kind, $provider]) {
        $insert('service_bookings', [
            'id' => $id, 'condo_id' => 'c1', 'type' => $type, 'date' => $day($offset), 'period' => $period,
            'unit' => $unit, 'resident_name' => $resident, 'status' => $status, 'repair_kind' => $kind,
            'provider' => $provider, 'created_at' => $at('-1 day'),
        ]);
    }

    // ------------------------------------------------------------ boletos
    // Do próximo mês (já emitido) até seis meses atrás, para cada apartamento com morador.
    $units = rows('SELECT DISTINCT u.condo_id, uu.unit FROM user_units uu JOIN users u ON u.id = uu.user_id');
    $currentMonth = new DateTimeImmutable('first day of this month');
    foreach ($units as $link) {
        for ($offset = -1; $offset < 7; $offset++) {
            $reference = $currentMonth->modify((-$offset) . ' months');
            $due = $reference->modify('+9 days');
            $paid = $offset > 0;
            $insert('bills', [
                'id' => uuid(),
                'condo_id' => $link['condo_id'],
                'unit' => $link['unit'],
                'reference' => $reference->format('Y-m'),
                'amount' => 685.40 + (($offset + 1) % 3) * 12.5,
                'due_date' => $due->format('Y-m-d'),
                'status' => $paid ? 'PAID' : 'OPEN',
                'barcode' => '34191.79001 01043.510047 91020.150008 ' . ((8 + $offset) % 10) . ' 9' . $reference->format('m') . '0000068540',
                'paid_at' => $paid ? $due->modify('-2 days')->format('Y-m-d') : null,
            ]);
        }
    }

    // ------------------------------------------------------------ encomendas
    $parcels = [
        ['p1', 'Caixa média', 'Correios', 'Porteiro José', '-2 hours', '4821', 'Apto 101 - Bloco A', null, null],
        ['p2', 'Envelope', 'Mercado Livre', 'Porteiro Antônio', '-1 day', '1937', 'Apto 101 - Bloco A', null, null],
        ['p3', 'Caixa grande', 'Amazon', 'Porteiro José', '-4 days', '7710', 'Apto 101 - Bloco A', '-3 days', 'Porteiro José'],
        ['p4', 'Pacote', 'Shopee', 'Porteiro Antônio', '-10 days', '2264', 'Apto 101 - Bloco A', '-10 days', 'Porteiro Antônio'],
        ['p5', 'Caixa de vinhos', 'Evino', 'Porteiro José', '-5 hours', '5306', 'Apto 1402 - Bloco A', null, null],
        ['p6', 'Envelope', 'Correios', 'Porteiro Antônio', '-7 hours', '3158', 'Apto 302 - Bloco B', null, null],
        ['p7', 'Caixa pequena', 'Mercado Livre', 'Porteiro Antônio', '-2 days', '6642', 'Apto 503 - Bloco B', null, null],
    ];
    foreach ($parcels as [$id, $description, $carrier, $receivedBy, $arrived, $code, $unit, $picked, $deliveredBy]) {
        $insert('parcels', [
            'id' => $id, 'condo_id' => 'c1', 'unit' => $unit, 'description' => $description, 'carrier' => $carrier,
            'received_by' => $receivedBy, 'arrived_at' => $at($arrived), 'pickup_code' => $code,
            'status' => $picked === null ? 'WAITING' : 'PICKED_UP',
            'picked_up_at' => $picked === null ? null : $at($picked), 'delivered_by' => $deliveredBy,
        ]);
    }

    // ------------------------------------------------------------ ocorrências
    $occurrences = [
        ['o1', 'Barulho após as 22h', 'Som alto vindo do apartamento acima durante a madrugada de sábado.', 'NOISE', 'RESOLVED', '-12 days', 'Apto 101 - Bloco A', 'Ana Ribeiro'],
        ['o2', 'Lâmpada queimada na garagem', 'Lâmpada próxima à vaga 32 do subsolo está queimada.', 'MAINTENANCE', 'IN_REVIEW', '-2 days', 'Apto 101 - Bloco A', 'Ana Ribeiro'],
        ['o3', 'Vazamento no hall do Bloco B', 'Água escorrendo pelo teto próximo ao elevador social do 3º andar.', 'MAINTENANCE', 'OPEN', '-5 hours', 'Apto 302 - Bloco B', 'Marina Costa'],
        ['o4', 'Carro na vaga de visitante', 'Veículo prata estacionado há três dias na vaga de visitantes.', 'GARAGE', 'OPEN', '-1 day', 'Apto 104 - Bloco A', 'Paulo Mendes'],
    ];
    foreach ($occurrences as [$id, $title, $description, $category, $status, $created, $unit, $author]) {
        $insert('occurrences', [
            'id' => $id, 'condo_id' => 'c1', 'title' => $title, 'description' => $description, 'category' => $category,
            'status' => $status, 'created_at' => $at($created), 'unit' => $unit, 'author_name' => $author, 'photo_ids' => '[]',
        ]);
    }
    $insert('occurrence_messages', [
        'occurrence_id' => 'o1', 'author' => 'Síndico - Carlos Andrade', 'from_management' => 1,
        'text' => 'O morador foi notificado conforme o regimento interno.', 'sent_at' => $at('-11 days'), 'photo_ids' => '[]',
    ]);

    // ------------------------------------------------------------ documentos (sem arquivo: o app gera o PDF de demonstração)
    $documents = [
        ['doc-regimento', 'c1', 'Regimento interno', 'BYLAWS', 420, 312],
        ['doc-convencao', 'c1', 'Convenção do condomínio', 'CONVENTION', 900, 540],
        ['doc-ata-ago', 'c1', 'Ata da AGO · prestação de contas', 'MINUTES', 45, 128],
        ['doc-ata-age', 'c1', 'Ata da AGE · reforma da fachada', 'MINUTES', 120, 96],
        ['doc-regimento-c2', 'c2', 'Regimento interno', 'BYLAWS', 300, 280],
    ];
    foreach ($documents as [$id, $condoId, $title, $category, $daysAgo, $sizeKb]) {
        $insert('condo_documents', [
            'id' => $id, 'condo_id' => $condoId, 'title' => $title, 'category' => $category, 'file_id' => null,
            'file_name' => "$id.pdf", 'size_bytes' => $sizeKb * 1024, 'uploaded_at' => $at("-$daysAgo days"),
            'uploaded_by' => 'Síndico - Carlos Andrade',
        ]);
    }
}
