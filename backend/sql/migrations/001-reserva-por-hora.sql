-- Espaços reservados por hora, além dos por turno. Toda reserva passa a guardar
-- a faixa de horas (o turno da tarde vira 13 a 17), usada para conferir conflitos.

ALTER TABLE common_areas
    ADD COLUMN booking_mode ENUM('PERIOD','HOURLY') NOT NULL DEFAULT 'PERIOD' AFTER icon_key,
    ADD COLUMN opens_at     TINYINT NOT NULL DEFAULT 8  AFTER booking_mode,
    ADD COLUMN closes_at    TINYINT NOT NULL DEFAULT 22 AFTER opens_at,
    ADD COLUMN max_hours    TINYINT NOT NULL DEFAULT 2  AFTER closes_at;

ALTER TABLE reservations
    MODIFY period ENUM('MORNING','AFTERNOON','NIGHT') NULL,
    ADD COLUMN start_hour TINYINT NOT NULL DEFAULT 0 AFTER period,
    ADD COLUMN end_hour   TINYINT NOT NULL DEFAULT 0 AFTER start_hour,
    DROP INDEX ix_reservations_slot,
    ADD KEY ix_reservations_slot (area_id, date);

UPDATE reservations SET
    start_hour = CASE period WHEN 'MORNING' THEN 8 WHEN 'AFTERNOON' THEN 13 ELSE 18 END,
    end_hour   = CASE period WHEN 'MORNING' THEN 12 WHEN 'AFTERNOON' THEN 17 ELSE 22 END
WHERE period IS NOT NULL;
