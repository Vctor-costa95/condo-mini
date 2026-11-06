-- Morador cria a própria conta pelo app; o pedido espera a administradora aceitar.
-- Só vira usuário (tabela users) quando é aceito.

CREATE TABLE IF NOT EXISTS signup_requests (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id      VARCHAR(36)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL DEFAULT '',
    phone         VARCHAR(40)  NOT NULL DEFAULT '',
    login         VARCHAR(80)  NOT NULL,
    login_key     VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    unit          VARCHAR(80)  NOT NULL,
    status        ENUM('PENDING','REJECTED') NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(255) NULL,
    ip            VARCHAR(45)  NOT NULL DEFAULT '',
    created_at    DATETIME     NOT NULL,
    UNIQUE KEY uq_signup_login (login_key),
    KEY idx_signup_condo (condo_id, status),
    KEY idx_signup_ip (ip, created_at),
    CONSTRAINT fk_signup_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
