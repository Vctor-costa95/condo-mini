<?php
// Boletos, encomendas, livro de ocorrências e documentos do condomínio.

declare(strict_types=1);

function bill_json(array $b): array
{
    $status = $b['status'] === 'OPEN' && $b['due_date'] < today() ? 'OVERDUE' : $b['status'];
    return [
        'id' => $b['id'],
        'reference' => $b['reference'],
        'amount' => (float) $b['amount'],
        'dueDate' => $b['due_date'],
        'status' => $status,
        'barcode' => $b['barcode'],
        'paidAt' => $b['paid_at'],
    ];
}

function unit_document_json(array $d): array
{
    return [
        'id' => $d['id'],
        'unit' => $d['unit'],
        'reference' => $d['reference'],
        'fileName' => $d['original_name'],
        'url' => file_url($d['file_id']),
        'sizeBytes' => (int) $d['size_bytes'],
        'uploadedAt' => iso($d['uploaded_at']),
        'condoId' => $d['condo_id'],
    ];
}

/** O código de retirada só aparece para o morador: a portaria pede ao morador. */
function parcel_json(array $p): array
{
    return [
        'id' => $p['id'],
        'description' => $p['description'],
        'carrier' => $p['carrier'],
        'receivedBy' => $p['received_by'],
        'arrivedAt' => iso($p['arrived_at']),
        'pickupCode' => current_user()['role'] === 'RESIDENT' ? $p['pickup_code'] : '',
        'status' => $p['status'],
        'pickedUpAt' => iso($p['picked_up_at']),
        'photoUrl' => $p['photo_file_id'] === null ? null : file_url($p['photo_file_id']),
        'unit' => $p['unit'],
        'condoId' => $p['condo_id'],
        'deliveredBy' => $p['delivered_by'],
    ];
}

function occurrence_json(array $o): array
{
    $messages = rows('SELECT * FROM occurrence_messages WHERE occurrence_id = ? ORDER BY sent_at, id', [$o['id']]);
    return [
        'id' => $o['id'],
        'protocol' => 'OC-' . $o['seq'],
        'title' => $o['title'],
        'description' => $o['description'],
        'category' => $o['category'],
        'status' => $o['status'],
        'createdAt' => iso($o['created_at']),
        'unit' => $o['unit'],
        'authorName' => $o['author_name'],
        'photoUrls' => file_urls($o['photo_ids']),
        'messages' => array_map(fn ($m) => [
            'author' => $m['author'],
            'fromManagement' => (bool) $m['from_management'],
            'text' => $m['text'],
            'sentAt' => iso($m['sent_at']),
            'photoUrls' => file_urls($m['photo_ids']),
        ], $messages),
    ];
}

/** url vazia = documento de demonstração, que o app gera localmente. */
function document_json(array $d): array
{
    return [
        'id' => $d['id'],
        'condoId' => $d['condo_id'],
        'title' => $d['title'],
        'category' => $d['category'],
        'fileName' => $d['file_name'],
        'url' => $d['file_id'] === null ? '' : file_url($d['file_id']),
        'sizeBytes' => (int) $d['size_bytes'],
        'uploadedAt' => iso($d['uploaded_at']),
        'uploadedBy' => $d['uploaded_by'],
    ];
}

const UNIT_DOCUMENT_SELECT = 'SELECT d.*, f.original_name, f.size_bytes FROM unit_documents d JOIN files f ON f.id = d.file_id';

/** Nova mensagem na conversa da ocorrência, já mudando o status. */
function add_occurrence_message(string $id, bool $fromManagement, string $nextStatus): never
{
    $me = current_user();
    $occurrence = require_same_condo(row('SELECT * FROM occurrences WHERE id = ?', [$id]), 'Ocorrência não encontrada.');
    if (!$fromManagement) {
        require_unit_access($occurrence['unit']);
    }
    $photos = own_file_ids((array) input('photoUrls', []), $occurrence['condo_id']);
    $text = required_string('text', 5000);
    transaction(function () use ($id, $me, $fromManagement, $nextStatus, $photos, $text) {
        exec_sql(
            'INSERT INTO occurrence_messages (occurrence_id, author, from_management, text, sent_at, photo_ids)
             VALUES (?, ?, ?, ?, ?, ?)',
            [$id, author_label($me), $fromManagement ? 1 : 0, $text, now_sql(), json_encode($photos)]
        );
        exec_sql('UPDATE occurrences SET status = ? WHERE id = ?', [$nextStatus, $id]);
    });
    $protocol = "OC-{$occurrence['seq']}";
    if ($fromManagement) {
        $title = match ($nextStatus) {
            'RESOLVED' => "Ocorrência $protocol resolvida",
            'NEEDS_INFO' => "Pedimos mais detalhes na $protocol",
            default => "Resposta na ocorrência $protocol",
        };
        notify(users_of_unit($occurrence['condo_id'], $occurrence['unit']), $title, $text, ['tab' => 'occurrences', 'id' => $id]);
    } else {
        notify(managers_of($occurrence['condo_id']), "Morador respondeu na $protocol", $text, ['tab' => 'manager_occurrences', 'id' => $id]);
    }
    send_json(occurrence_json(row('SELECT * FROM occurrences WHERE id = ?', [$id])));
}

/** 4 dígitos, sem repetir entre as encomendas que ainda aguardam retirada. */
function new_pickup_code(string $condoId): string
{
    $inUse = array_flip(array_column(
        rows("SELECT pickup_code FROM parcels WHERE condo_id = ? AND status = 'WAITING'", [$condoId]),
        'pickup_code'
    ));
    do {
        $code = (string) random_int(1000, 9999);
    } while (isset($inUse[$code]));
    return $code;
}

function register_record_routes(Router $r): void
{
    // ------------------------------------------------------------ boletos

    $r->add('GET', '/bills', function () {
        $unit = require_unit_access(required_query('unit'));
        $list = rows('SELECT * FROM bills WHERE condo_id = ? AND unit = ? ORDER BY reference DESC', [condo_id(), $unit]);
        send_json(array_map('bill_json', $list));
    });

    $r->add('GET', '/bills/{id}', function (string $id) {
        $bill = require_same_condo(row('SELECT * FROM bills WHERE id = ?', [$id]), 'Boleto não encontrado.');
        require_unit_access($bill['unit']);
        send_json(bill_json($bill));
    });

    // PDF enviado pela administração para o mês do boleto; 404 quando não há.
    $r->add('GET', '/bills/{id}/pdf', function (string $id) {
        $bill = require_same_condo(row('SELECT * FROM bills WHERE id = ?', [$id]), 'Boleto não encontrado.');
        require_unit_access($bill['unit']);
        $file = row(
            'SELECT f.* FROM unit_documents d JOIN files f ON f.id = d.file_id
             WHERE d.condo_id = ? AND d.unit = ? AND d.reference = ?',
            [$bill['condo_id'], $bill['unit'], $bill['reference']]
        ) ?? throw new ApiError(404, 'Ainda não há PDF para este boleto.');
        send_file(file_path($file), $file['mime'], $file['original_name']);
    });

    $r->add('GET', '/unit-documents', function () {
        require_role('ADMIN', 'MANAGER');
        $list = rows(UNIT_DOCUMENT_SELECT . ' WHERE d.condo_id = ? ORDER BY d.uploaded_at DESC', [condo_id()]);
        send_json(array_map('unit_document_json', $list));
    });

    // Um PDF por unidade e mês: o novo substitui o anterior.
    $r->add('POST', '/unit-documents', function () {
        require_role('ADMIN');
        $condoId = condo_id();
        $unit = required_string('unit', 80);
        $reference = require_month(required_string('reference', 7));
        $file = store_upload($condoId, PDF_TYPES);
        $previous = row('SELECT * FROM unit_documents WHERE condo_id = ? AND unit = ? AND reference = ?', [$condoId, $unit, $reference]);
        $id = uuid();
        transaction(function () use ($id, $condoId, $unit, $reference, $file, $previous) {
            if ($previous !== null) {
                exec_sql('DELETE FROM unit_documents WHERE id = ?', [$previous['id']]);
            }
            exec_sql(
                'INSERT INTO unit_documents (id, condo_id, unit, reference, file_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?)',
                [$id, $condoId, $unit, $reference, $file['id'], now_sql()]
            );
        });
        delete_file($previous['file_id'] ?? null);
        notify(
            users_of_unit($condoId, $unit),
            'Boleto disponível',
            'O boleto de ' . month_label($reference) . ' já está no app.',
            ['tab' => 'bills']
        );
        send_json(unit_document_json(row(UNIT_DOCUMENT_SELECT . ' WHERE d.id = ?', [$id])), 201);
    });

    $r->add('DELETE', '/unit-documents/{id}', function (string $id) {
        require_role('ADMIN');
        $document = require_same_condo(row('SELECT * FROM unit_documents WHERE id = ?', [$id]), 'Documento não encontrado.');
        exec_sql('DELETE FROM unit_documents WHERE id = ?', [$id]);
        delete_file($document['file_id']);
        send_json(['ok' => true]);
    });

    // ------------------------------------------------------------ encomendas

    // Com ?unit=, as do apartamento; sem, todas do condomínio (portaria).
    $r->add('GET', '/parcels', function () {
        $condoId = condo_id();
        $unit = query('unit', '');
        if ($unit === '') {
            require_role('DOORMAN', 'MANAGER', 'ADMIN');
            $list = rows('SELECT * FROM parcels WHERE condo_id = ? ORDER BY arrived_at DESC', [$condoId]);
        } else {
            require_unit_access($unit);
            $list = rows('SELECT * FROM parcels WHERE condo_id = ? AND unit = ? ORDER BY arrived_at DESC', [$condoId, $unit]);
        }
        send_json(array_map('parcel_json', $list));
    });

    $r->add('POST', '/parcels', function () {
        $me = require_role('DOORMAN', 'MANAGER', 'ADMIN');
        $condoId = condo_id();
        $photo = own_file_ids(array_filter([(string) input('photoUrl', '')]), $condoId)[0] ?? null;
        $parcel = [
            'id' => uuid(),
            'condo_id' => $condoId,
            'unit' => required_string('unit', 80),
            'description' => required_string('description', 160),
            'carrier' => optional_string('carrier', 80),
            'received_by' => author_label($me),
            'arrived_at' => now_sql(),
            'pickup_code' => new_pickup_code($condoId),
            'status' => 'WAITING',
            'picked_up_at' => null,
            'photo_file_id' => $photo,
            'delivered_by' => null,
        ];
        exec_sql(
            'INSERT INTO parcels (id, condo_id, unit, description, carrier, received_by, arrived_at, pickup_code, status, picked_up_at, photo_file_id, delivered_by)
             VALUES (:id, :condo_id, :unit, :description, :carrier, :received_by, :arrived_at, :pickup_code, :status, :picked_up_at, :photo_file_id, :delivered_by)',
            $parcel
        );
        // Sem o código: a notificação aparece na tela bloqueada.
        notify(
            users_of_unit($condoId, $parcel['unit']),
            'Encomenda na portaria',
            "{$parcel['description']} para o {$parcel['unit']}. O código de retirada está no app.",
            ['tab' => 'parcels', 'id' => $parcel['id']]
        );
        send_json(parcel_json($parcel), 201);
    });

    // Entrega ao morador; falha se o código não for o da encomenda.
    $r->add('POST', '/parcels/{id}/deliver', function (string $id) {
        $me = require_role('DOORMAN', 'MANAGER', 'ADMIN');
        $parcel = require_same_condo(row('SELECT * FROM parcels WHERE id = ?', [$id]), 'Encomenda não encontrada.');
        if ($parcel['status'] !== 'WAITING') {
            throw new ApiError(409, 'Esta encomenda já foi entregue.');
        }
        if (!hash_equals($parcel['pickup_code'], trim((string) input('code', '')))) {
            throw new ApiError(422, 'Código não confere. Peça ao morador para conferir no app.');
        }
        exec_sql(
            "UPDATE parcels SET status = 'PICKED_UP', picked_up_at = ?, delivered_by = ? WHERE id = ?",
            [now_sql(), author_label($me), $id]
        );
        send_json(parcel_json(row('SELECT * FROM parcels WHERE id = ?', [$id])));
    });

    // ------------------------------------------------------------ ocorrências

    // Com ?unit=, as do apartamento; sem, todas do condomínio (síndico).
    $r->add('GET', '/occurrences', function () {
        $condoId = condo_id();
        $unit = query('unit', '');
        if ($unit === '') {
            require_role('MANAGER', 'ADMIN');
            $list = rows('SELECT * FROM occurrences WHERE condo_id = ? ORDER BY created_at DESC', [$condoId]);
        } else {
            require_unit_access($unit);
            $list = rows('SELECT * FROM occurrences WHERE condo_id = ? AND unit = ? ORDER BY created_at DESC', [$condoId, $unit]);
        }
        send_json(array_map('occurrence_json', $list));
    });

    $r->add('POST', '/occurrences', function () {
        $me = current_user();
        $condoId = condo_id();
        $id = uuid();
        exec_sql(
            'INSERT INTO occurrences (id, condo_id, title, description, category, status, created_at, unit, author_name, photo_ids)
             VALUES (?, ?, ?, ?, ?, \'OPEN\', ?, ?, ?, ?)',
            [
                $id,
                $condoId,
                required_string('title', 200),
                required_string('description', 5000),
                require_enum((string) input('category', ''), ['NOISE', 'MAINTENANCE', 'SECURITY', 'GARAGE', 'CLEANING', 'OTHER'], 'category'),
                now_sql(),
                require_unit_access(required_string('unit', 80)),
                $me['name'],
                json_encode(own_file_ids((array) input('photoUrls', []), $condoId)),
            ]
        );
        $occurrence = row('SELECT * FROM occurrences WHERE id = ?', [$id]);
        notify(
            managers_of($condoId),
            "Nova ocorrência OC-{$occurrence['seq']}",
            "{$occurrence['title']} · {$occurrence['unit']}",
            ['tab' => 'manager_occurrences', 'id' => $id]
        );
        send_json(occurrence_json($occurrence), 201);
    });

    // Resposta da administração; resolve=true encerra a ocorrência.
    $r->add('POST', '/occurrences/{id}/respond', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        add_occurrence_message($id, true, input('resolve', false) ? 'RESOLVED' : 'IN_REVIEW');
    });

    $r->add('POST', '/occurrences/{id}/request-details', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        add_occurrence_message($id, true, 'NEEDS_INFO');
    });

    // Complemento do morador devolve a ocorrência para análise.
    $r->add('POST', '/occurrences/{id}/reply', function (string $id) {
        require_role('RESIDENT');
        add_occurrence_message($id, false, 'IN_REVIEW');
    });

    // ------------------------------------------------------------ documentos

    $r->add('GET', '/documents', function () {
        $list = rows('SELECT * FROM condo_documents WHERE condo_id = ? ORDER BY uploaded_at DESC', [condo_id()]);
        send_json(array_map('document_json', $list));
    });

    $r->add('GET', '/documents/{id}', function (string $id) {
        send_json(document_json(require_same_condo(row('SELECT * FROM condo_documents WHERE id = ?', [$id]), 'Documento não encontrado.')));
    });

    $r->add('POST', '/documents', function () {
        $me = require_role('MANAGER', 'ADMIN');
        $condoId = condo_id();
        $title = required_string('title', 200);
        $category = require_enum((string) input('category', ''), ['BYLAWS', 'CONVENTION', 'MINUTES', 'OTHER'], 'category');
        $file = store_upload($condoId, PDF_TYPES);
        $document = [
            'id' => uuid(),
            'condo_id' => $condoId,
            'title' => $title,
            'category' => $category,
            'file_id' => $file['id'],
            'file_name' => $file['original_name'],
            'size_bytes' => $file['size_bytes'],
            'uploaded_at' => now_sql(),
            'uploaded_by' => author_label($me),
        ];
        exec_sql(
            'INSERT INTO condo_documents (id, condo_id, title, category, file_id, file_name, size_bytes, uploaded_at, uploaded_by)
             VALUES (:id, :condo_id, :title, :category, :file_id, :file_name, :size_bytes, :uploaded_at, :uploaded_by)',
            $document
        );
        notify(everyone_in($condoId), 'Novo documento do condomínio', $title, ['tab' => 'documents', 'id' => $document['id']]);
        send_json(document_json($document), 201);
    });

    $r->add('DELETE', '/documents/{id}', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        $document = require_same_condo(row('SELECT * FROM condo_documents WHERE id = ?', [$id]), 'Documento não encontrado.');
        exec_sql('DELETE FROM condo_documents WHERE id = ?', [$id]);
        delete_file($document['file_id']);
        send_json(['ok' => true]);
    });
}
