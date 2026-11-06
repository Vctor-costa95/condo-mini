-- Espaços reservados pelo dia inteiro. A reserva guarda 0h às 24h, então a conferência
-- de conflitos por faixa de horas já bloqueia qualquer outra reserva na data.

ALTER TABLE common_areas
    MODIFY booking_mode ENUM('PERIOD','HOURLY','DAILY') NOT NULL DEFAULT 'PERIOD';
