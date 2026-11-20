-- "Esqueci a senha": no máximo 5 códigos por pessoa a cada 24 horas. Antes, cada código
-- novo zerava as 5 tentativas e dava para pedir um por minuto, o que permitia chutar o
-- código de 6 dígitos milhares de vezes por dia.

ALTER TABLE password_resets
    ADD COLUMN codes_sent        TINYINT  NOT NULL DEFAULT 1 AFTER attempts,
    ADD COLUMN window_started_at DATETIME NULL AFTER codes_sent;

UPDATE password_resets SET window_started_at = created_at WHERE window_started_at IS NULL;
