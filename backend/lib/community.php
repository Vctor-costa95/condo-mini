<?php
// Avisos, espaços comuns, reservas e agendamentos de mudança e reparo.

declare(strict_types=1);

const PERIODS = ['MORNING', 'AFTERNOON', 'NIGHT'];
const SERVICE_PERIODS = ['MORNING', 'AFTERNOON'];
const PERIOD_ORDER = "FIELD(period, 'MORNING', 'AFTERNOON', 'NIGHT')";
// Recusadas e canceladas liberam o horário.
const HOLDS_SLOT = "status IN ('CONFIRMED', 'PENDING')";

function notice_json(array $n): array
{
    return [
        'id' => $n['id'],
        'title' => $n['title'],
        'body' => $n['body'],
        'category' => $n['category'],
        'author' => $n['author'],
        'publishedAt' => iso($n['published_at']),
        'isPinned' => (bool) $n['pinned'],
    ];
}

function area_json(array $a): array
{
    return [
        'id' => $a['id'],
        'name' => $a['name'],
        'description' => $a['description'],
        'capacity' => (int) $a['capacity'],
        'fee' => (float) $a['fee'],
        'rules' => $a['rules'],
        'iconKey' => $a['icon_key'],
        'closedReason' => $a['closed_reason'],
        'condoId' => $a['condo_id'],
    ];
}

function reservation_json(array $r): array
{
    $area = row('SELECT * FROM common_areas WHERE id = ?', [$r['area_id']]);
    return [
        'id' => $r['id'],
        'area' => area_json($area),
        'date' => $r['date'],
        'period' => $r['period'],
        'unit' => $r['unit'],
        'status' => $r['status'],
        'residentName' => $r['resident_name'],
        'rejectionReason' => $r['rejection_reason'],
    ];
}

function booking_json(array $b): array
{
    return [
        'id' => $b['id'],
        'type' => $b['type'],
        'date' => $b['date'],
        'period' => $b['period'],
        'unit' => $b['unit'],
        'residentName' => $b['resident_name'],
        'condoId' => $b['condo_id'],
        'status' => $b['status'],
        'repairKind' => $b['repair_kind'],
        'provider' => $b['provider'],
        'rejectionReason' => $b['rejection_reason'],
    ];
}

function find_area(string $id): array
{
    return require_same_condo(
        row('SELECT * FROM common_areas WHERE id = ? AND deleted_at IS NULL', [$id]),
        'Área não encontrada.'
    );
}

/** Reserva que o morador do apartamento ou a administração podem alterar. */
function find_reservation(string $id): array
{
    $found = require_same_condo(row('SELECT * FROM reservations WHERE id = ?', [$id]), 'Reserva não encontrada.');
    require_unit_access($found['unit']);
    return $found;
}

function find_booking(string $id): array
{
    $found = require_same_condo(row('SELECT * FROM service_bookings WHERE id = ?', [$id]), 'Agendamento não encontrado.');
    require_unit_access($found['unit']);
    return $found;
}

/** Avisa o apartamento da reserva: "Salão de festas · 12/11 · tarde", mais o motivo da recusa. */
function notify_reservation(array $reservation, string $title, string $reason): void
{
    $area = row('SELECT name FROM common_areas WHERE id = ?', [$reservation['area_id']]);
    $body = ($area['name'] ?? 'Espaço') . ' · ' . slot_label($reservation['date'], $reservation['period']);
    notify(
        users_of_unit($reservation['condo_id'], $reservation['unit']),
        $title,
        $reason === '' ? $body : "$body. Motivo: $reason",
        ['tab' => 'reservations', 'id' => $reservation['id']]
    );
}

function notify_booking(array $booking, string $title, string $reason): void
{
    $body = service_label($booking) . ' · ' . slot_label($booking['date'], $booking['period']);
    notify(
        users_of_unit($booking['condo_id'], $booking['unit']),
        $title,
        $reason === '' ? $body : "$body. Motivo: $reason",
        ['tab' => 'reservations', 'id' => $booking['id']]
    );
}

/** @return list<string> */
function taken_move_periods(string $condoId, string $date): array
{
    return array_column(rows(
        "SELECT DISTINCT period FROM service_bookings
         WHERE condo_id = ? AND type = 'MOVE' AND date = ? AND " . HOLDS_SLOT,
        [$condoId, $date]
    ), 'period');
}

function register_community_routes(Router $r): void
{
    // ------------------------------------------------------------ avisos

    $r->add('GET', '/notices', function () {
        $list = rows('SELECT * FROM notices WHERE condo_id = ? ORDER BY published_at DESC', [condo_id()]);
        send_json(array_map('notice_json', $list));
    });

    $r->add('POST', '/notices', function () {
        $me = require_role('MANAGER', 'ADMIN');
        $notice = [
            'id' => uuid(),
            'condo_id' => condo_id(),
            'title' => required_string('title', 200),
            'body' => required_string('body', 10000),
            'category' => require_enum((string) input('category', ''), ['MANAGER', 'IMPORTANT', 'MAINTENANCE', 'EVENT'], 'category'),
            'author' => author_label($me),
            'published_at' => now_sql(),
            'pinned' => input('pinned', false) ? 1 : 0,
        ];
        exec_sql(
            'INSERT INTO notices (id, condo_id, title, body, category, author, published_at, pinned)
             VALUES (:id, :condo_id, :title, :body, :category, :author, :published_at, :pinned)',
            $notice
        );
        notify(everyone_in($notice['condo_id']), $notice['title'], $notice['body'], ['tab' => 'notices', 'id' => $notice['id']]);
        send_json(notice_json($notice), 201);
    });

    $r->add('DELETE', '/notices/{id}', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        require_same_condo(row('SELECT * FROM notices WHERE id = ?', [$id]), 'Aviso não encontrado.');
        exec_sql('DELETE FROM notices WHERE id = ?', [$id]);
        send_json(['ok' => true]);
    });

    // ------------------------------------------------------------ espaços comuns

    $r->add('GET', '/areas', function () {
        $list = rows('SELECT * FROM common_areas WHERE condo_id = ? AND deleted_at IS NULL ORDER BY name', [condo_id()]);
        send_json(array_map('area_json', $list));
    });

    // Cria quando não há id; senão atualiza.
    $r->add('POST', '/areas', function () {
        require_role('MANAGER', 'ADMIN');
        $id = optional_string('id', 36);
        $condoId = $id === '' ? condo_id() : find_area($id)['condo_id'];
        $closed = trim((string) input('closedReason', ''));
        $area = [
            'id' => $id ?: uuid(),
            'condo_id' => $condoId,
            'name' => required_string('name', 120),
            'description' => optional_string('description', 2000),
            'capacity' => max(0, (int) input('capacity', 0)),
            'fee' => max(0, round((float) input('fee', 0), 2)),
            'rules' => optional_string('rules', 2000),
            'icon_key' => optional_string('iconKey', 40),
            'closed_reason' => $closed === '' ? null : mb_substr($closed, 0, 255),
        ];
        exec_sql(
            'INSERT INTO common_areas (id, condo_id, name, description, capacity, fee, rules, icon_key, closed_reason)
             VALUES (:id, :condo_id, :name, :description, :capacity, :fee, :rules, :icon_key, :closed_reason)
             ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description),
               capacity = VALUES(capacity), fee = VALUES(fee), rules = VALUES(rules),
               icon_key = VALUES(icon_key), closed_reason = VALUES(closed_reason)',
            $area
        );
        send_json(area_json($area));
    });

    // Remove o espaço e cancela as reservas futuras dele.
    $r->add('DELETE', '/areas/{id}', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        find_area($id);
        transaction(function () use ($id) {
            exec_sql('UPDATE common_areas SET deleted_at = ? WHERE id = ?', [now_sql(), $id]);
            exec_sql(
                "UPDATE reservations SET status = 'CANCELED' WHERE area_id = ? AND date >= ? AND " . HOLDS_SLOT,
                [$id, today()]
            );
        });
        send_json(['ok' => true]);
    });

    // Interdita com um motivo, ou libera quando reason é null.
    $r->add('PUT', '/areas/{id}/closed', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        find_area($id);
        $reason = input('reason');
        $reason = $reason === null ? null : (mb_substr(trim((string) $reason), 0, 255) ?: 'Em manutenção');
        exec_sql('UPDATE common_areas SET closed_reason = ? WHERE id = ?', [$reason, $id]);
        send_json(area_json(row('SELECT * FROM common_areas WHERE id = ?', [$id])));
    });

    $r->add('GET', '/areas/{id}/booked', function (string $id) {
        find_area($id);
        $date = require_date(query('date', ''));
        send_json(array_column(rows(
            'SELECT DISTINCT period FROM reservations WHERE area_id = ? AND date = ? AND ' . HOLDS_SLOT,
            [$id, $date]
        ), 'period'));
    });

    // ------------------------------------------------------------ reservas

    // Com ?unit=, as do apartamento; sem, todas do condomínio (síndico e portaria).
    $r->add('GET', '/reservations', function () {
        $condoId = condo_id();
        $unit = query('unit', '');
        if ($unit === '') {
            require_role('MANAGER', 'ADMIN', 'DOORMAN');
            $list = rows("SELECT * FROM reservations WHERE condo_id = ? AND status <> 'CANCELED' ORDER BY date, " . PERIOD_ORDER, [$condoId]);
        } else {
            require_unit_access($unit);
            $list = rows("SELECT * FROM reservations WHERE condo_id = ? AND unit = ? AND status <> 'CANCELED' ORDER BY date, " . PERIOD_ORDER, [$condoId, $unit]);
        }
        send_json(array_map('reservation_json', $list));
    });

    $r->add('POST', '/reservations', function () {
        $me = current_user();
        $area = find_area(required_string('areaId', 36));
        $date = require_date(required_string('date', 10));
        $period = require_enum((string) input('period', ''), PERIODS, 'period');
        $unit = require_unit_access(required_string('unit', 80));
        if ($area['closed_reason'] !== null) {
            throw new ApiError(409, 'Espaço interditado: ' . $area['closed_reason']);
        }
        if ($date < today()) {
            throw new ApiError(422, 'Escolha uma data a partir de hoje.');
        }
        $reservation = transaction(function () use ($area, $date, $period, $unit, $me) {
            // Trava o horário para duas reservas simultâneas não passarem juntas.
            $taken = row(
                'SELECT id FROM reservations WHERE area_id = ? AND date = ? AND period = ? AND ' . HOLDS_SLOT . ' FOR UPDATE',
                [$area['id'], $date, $period]
            );
            if ($taken !== null) {
                throw new ApiError(409, 'Este período já está reservado.');
            }
            $reservation = [
                'id' => uuid(),
                'condo_id' => $area['condo_id'],
                'area_id' => $area['id'],
                'date' => $date,
                'period' => $period,
                'unit' => $unit,
                // Áreas com taxa dependem de aprovação do síndico.
                'status' => (float) $area['fee'] > 0 ? 'PENDING' : 'CONFIRMED',
                'resident_name' => $me['name'],
                'rejection_reason' => null,
                'created_at' => now_sql(),
            ];
            exec_sql(
                'INSERT INTO reservations (id, condo_id, area_id, date, period, unit, status, resident_name, rejection_reason, created_at)
                 VALUES (:id, :condo_id, :area_id, :date, :period, :unit, :status, :resident_name, :rejection_reason, :created_at)',
                $reservation
            );
            return $reservation;
        });
        if ($reservation['status'] === 'PENDING') {
            notify(
                managers_of($reservation['condo_id']),
                'Reserva para aprovar',
                "{$area['name']} · " . slot_label($date, $period) . " · $unit",
                ['tab' => 'manager_reservations', 'id' => $reservation['id']]
            );
        }
        send_json(reservation_json($reservation), 201);
    });

    $r->add('POST', '/reservations/{id}/cancel', function (string $id) {
        require_role('RESIDENT', 'MANAGER', 'ADMIN');
        find_reservation($id);
        exec_sql("UPDATE reservations SET status = 'CANCELED' WHERE id = ?", [$id]);
        send_json(reservation_json(row('SELECT * FROM reservations WHERE id = ?', [$id])));
    });

    $r->add('POST', '/reservations/{id}/approve', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        $reservation = find_reservation($id);
        exec_sql("UPDATE reservations SET status = 'CONFIRMED' WHERE id = ?", [$id]);
        notify_reservation($reservation, 'Reserva confirmada', '');
        send_json(reservation_json(row('SELECT * FROM reservations WHERE id = ?', [$id])));
    });

    $r->add('POST', '/reservations/{id}/reject', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        $reservation = find_reservation($id);
        $reason = optional_string('reason');
        exec_sql(
            "UPDATE reservations SET status = 'REJECTED', rejection_reason = ? WHERE id = ?",
            [$reason ?: null, $id]
        );
        notify_reservation($reservation, 'Reserva recusada', $reason);
        send_json(reservation_json(row('SELECT * FROM reservations WHERE id = ?', [$id])));
    });

    // ------------------------------------------------------------ mudanças e reparos

    $r->add('GET', '/service-bookings', function () {
        $condoId = condo_id();
        $unit = query('unit', '');
        if ($unit === '') {
            require_role('MANAGER', 'ADMIN', 'DOORMAN');
            $list = rows("SELECT * FROM service_bookings WHERE condo_id = ? AND status <> 'CANCELED' ORDER BY date, " . PERIOD_ORDER, [$condoId]);
        } else {
            require_unit_access($unit);
            $list = rows("SELECT * FROM service_bookings WHERE condo_id = ? AND unit = ? AND status <> 'CANCELED' ORDER BY date, " . PERIOD_ORDER, [$condoId, $unit]);
        }
        send_json(array_map('booking_json', $list));
    });

    // Só uma mudança por manhã e uma por tarde.
    $r->add('GET', '/service-bookings/taken-moves', function () {
        send_json(taken_move_periods(condo_id(), require_date(query('date', ''))));
    });

    $r->add('POST', '/service-bookings', function () {
        $me = current_user();
        $condoId = condo_id();
        $type = require_enum((string) input('type', ''), ['MOVE', 'REPAIR'], 'type');
        $date = require_date(required_string('date', 10));
        $period = (string) input('period', '');
        if (!in_array($period, SERVICE_PERIODS, true)) {
            throw new ApiError(422, 'Escolha manhã ou tarde.');
        }
        if ($date < today()) {
            throw new ApiError(422, 'Escolha uma data a partir de hoje.');
        }
        $repairKind = $type === 'REPAIR'
            ? require_enum((string) input('repairKind', 'OTHER'), ['INTERNET', 'ELECTRICAL', 'PLUMBING', 'OTHER'], 'repairKind')
            : null;
        $booking = [
            'id' => uuid(),
            'condo_id' => $condoId,
            'type' => $type,
            'date' => $date,
            'period' => $period,
            'unit' => require_unit_access(required_string('unit', 80)),
            'resident_name' => $me['name'],
            'status' => 'PENDING',
            'repair_kind' => $repairKind,
            'provider' => optional_string('provider', 120),
            'rejection_reason' => null,
            'created_at' => now_sql(),
        ];
        transaction(function () use ($booking) {
            if ($booking['type'] === 'MOVE' && in_array($booking['period'], taken_move_periods($booking['condo_id'], $booking['date']), true)) {
                throw new ApiError(409, 'Já existe uma mudança neste período.');
            }
            exec_sql(
                'INSERT INTO service_bookings (id, condo_id, type, date, period, unit, resident_name, status, repair_kind, provider, rejection_reason, created_at)
                 VALUES (:id, :condo_id, :type, :date, :period, :unit, :resident_name, :status, :repair_kind, :provider, :rejection_reason, :created_at)',
                $booking
            );
        });
        notify(
            managers_of($condoId),
            service_label($booking) . ' para aprovar',
            slot_label($booking['date'], $booking['period']) . " · {$booking['unit']}",
            ['tab' => 'manager_reservations', 'id' => $booking['id']]
        );
        send_json(booking_json($booking), 201);
    });

    $r->add('POST', '/service-bookings/{id}/cancel', function (string $id) {
        require_role('RESIDENT', 'MANAGER', 'ADMIN');
        find_booking($id);
        exec_sql("UPDATE service_bookings SET status = 'CANCELED' WHERE id = ?", [$id]);
        send_json(booking_json(row('SELECT * FROM service_bookings WHERE id = ?', [$id])));
    });

    $r->add('POST', '/service-bookings/{id}/approve', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        $booking = find_booking($id);
        $clash = $booking['type'] === 'MOVE' && row(
            "SELECT id FROM service_bookings WHERE id <> ? AND condo_id = ? AND type = 'MOVE'
             AND date = ? AND period = ? AND status = 'CONFIRMED'",
            [$id, $booking['condo_id'], $booking['date'], $booking['period']]
        ) !== null;
        if ($clash) {
            throw new ApiError(409, 'Já há uma mudança aprovada neste período.');
        }
        exec_sql("UPDATE service_bookings SET status = 'CONFIRMED' WHERE id = ?", [$id]);
        notify_booking($booking, service_label($booking) . ($booking['type'] === 'MOVE' ? ' aprovada' : ' aprovado'), '');
        send_json(booking_json(row('SELECT * FROM service_bookings WHERE id = ?', [$id])));
    });

    $r->add('POST', '/service-bookings/{id}/reject', function (string $id) {
        require_role('MANAGER', 'ADMIN');
        $booking = find_booking($id);
        $reason = optional_string('reason');
        exec_sql(
            "UPDATE service_bookings SET status = 'REJECTED', rejection_reason = ? WHERE id = ?",
            [$reason ?: null, $id]
        );
        notify_booking($booking, service_label($booking) . ($booking['type'] === 'MOVE' ? ' recusada' : ' recusado'), $reason);
        send_json(booking_json(row('SELECT * FROM service_bookings WHERE id = ?', [$id])));
    });
}
