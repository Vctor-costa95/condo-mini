-- Três coisas novas, juntas para publicar de uma vez:
-- 1. A portaria marca a chegada de cada convidado da reserva (o morador é avisado).
-- 2. Código por e-mail para a pessoa trocar a senha sozinha.
-- 3. Baixa do boleto: na mão pela administração ou pelo arquivo de retorno do banco.

CREATE TABLE IF NOT EXISTS guest_arrivals (
    reservation_id VARCHAR(36)  NOT NULL,
    -- Mesmo nome da lista de convidados (a comparação ignora maiúsculas, pela collation).
    name           VARCHAR(80)  NOT NULL,
    arrived_at     DATETIME     NOT NULL,
    registered_by  VARCHAR(36)  NULL,
    PRIMARY KEY (reservation_id, name),
    CONSTRAINT fk_arrivals_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Um código ativo por pessoa: pedir de novo substitui o anterior.
CREATE TABLE IF NOT EXISTS password_resets (
    user_id    VARCHAR(36) NOT NULL PRIMARY KEY,
    code_hash  CHAR(64)    NOT NULL,
    expires_at DATETIME    NOT NULL,
    attempts   TINYINT     NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL,
    CONSTRAINT fk_resets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE bills
    ADD COLUMN paid_via    ENUM('MANUAL','BANK_RETURN') NULL AFTER paid_at,
    ADD COLUMN paid_amount DECIMAL(10,2) NULL AFTER paid_via,
    ADD COLUMN paid_by     VARCHAR(36)   NULL AFTER paid_amount;
