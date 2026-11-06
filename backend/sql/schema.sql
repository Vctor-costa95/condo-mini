-- Esquema do Condo-mini (MySQL 8 / MariaDB 10.6+).
-- Ids são UUIDs em texto, como no app; apartamentos são guardados pelo rótulo
-- ("Apto 101 - Bloco A"), o mesmo texto de User.unit.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS condominiums (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    address     VARCHAR(255) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- floor_overrides: {"andar": quantidade}, ex. {"14": 2}.
CREATE TABLE IF NOT EXISTS towers (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id        VARCHAR(36)  NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    floors          INT          NOT NULL,
    units_per_floor INT          NOT NULL,
    floor_overrides JSON         NOT NULL,
    position        INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_towers_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- password_hash NULL = conta de teste sem senha (só entra com allow_passwordless_logins).
CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id      VARCHAR(36)  NULL,
    name          VARCHAR(120) NOT NULL,
    role          ENUM('ADMIN','MANAGER','DOORMAN','RESIDENT') NOT NULL,
    email         VARCHAR(160) NOT NULL DEFAULT '',
    phone         VARCHAR(40)  NOT NULL DEFAULT '',
    login         VARCHAR(80)  NOT NULL,
    login_key     VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(255) NULL,
    UNIQUE KEY uq_users_login (login_key),
    CONSTRAINT fk_users_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_units (
    user_id  VARCHAR(36)  NOT NULL,
    unit     VARCHAR(80)  NOT NULL,
    position INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, unit),
    CONSTRAINT fk_user_units_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


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

-- Guarda só o SHA-256 do token entregue ao app.
CREATE TABLE IF NOT EXISTS sessions (
    token_hash   CHAR(64)    NOT NULL PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL,
    created_at   DATETIME    NOT NULL,
    last_used_at DATETIME    NOT NULL,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Celulares que recebem notificações (token do Firebase Cloud Messaging). Cada um
-- pertence a uma sessão: sair da conta ou excluir o cadastro para os envios.
CREATE TABLE IF NOT EXISTS devices (
    token        VARCHAR(512) CHARACTER SET ascii NOT NULL PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    session_hash CHAR(64)     NOT NULL,
    platform     VARCHAR(20)  NOT NULL DEFAULT 'android',
    updated_at   DATETIME     NOT NULL,
    KEY ix_devices_user (user_id),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_devices_session FOREIGN KEY (session_hash) REFERENCES sessions(token_hash) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fotos e PDFs enviados pelo app; o arquivo fica em storage/, fora do alcance público.
CREATE TABLE IF NOT EXISTS files (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id      VARCHAR(36)  NOT NULL,
    stored_name   VARCHAR(80)  NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime          VARCHAR(80)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    created_by    VARCHAR(36)  NULL,
    created_at    DATETIME     NOT NULL,
    CONSTRAINT fk_files_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notices (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id     VARCHAR(36)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT         NOT NULL,
    category     ENUM('MANAGER','IMPORTANT','MAINTENANCE','EVENT') NOT NULL,
    author       VARCHAR(160) NOT NULL,
    published_at DATETIME     NOT NULL,
    pinned       TINYINT(1)   NOT NULL DEFAULT 0,
    KEY ix_notices_condo (condo_id, published_at),
    CONSTRAINT fk_notices_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Espaço excluído vira deleted_at: as reservas antigas continuam mostrando o nome dele.
-- booking_mode: PERIOD (manhã, tarde, noite), HOURLY (início e duração, das opens_at
-- às closes_at, até max_hours seguidas) ou DAILY (o dia inteiro, guardado como 0h às 24h).
-- A taxa é por reserva em todos.
CREATE TABLE IF NOT EXISTS common_areas (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    condo_id      VARCHAR(36)   NOT NULL,
    name          VARCHAR(120)  NOT NULL,
    description   TEXT          NOT NULL,
    capacity      INT           NOT NULL DEFAULT 0,
    fee           DECIMAL(10,2) NOT NULL DEFAULT 0,
    rules         TEXT          NOT NULL,
    icon_key      VARCHAR(40)   NOT NULL DEFAULT '',
    booking_mode  ENUM('PERIOD','HOURLY','DAILY') NOT NULL DEFAULT 'PERIOD',
    opens_at      TINYINT       NOT NULL DEFAULT 8,
    closes_at     TINYINT       NOT NULL DEFAULT 22,
    max_hours     TINYINT       NOT NULL DEFAULT 2,
    closed_reason VARCHAR(255)  NULL,
    deleted_at    DATETIME      NULL,
    CONSTRAINT fk_areas_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reservations (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id         VARCHAR(36)  NOT NULL,
    area_id          VARCHAR(36)  NOT NULL,
    date             DATE         NOT NULL,
    -- Turno (espaços por turno) ou null (por hora). As horas valem para os dois:
    -- o turno da tarde é 13–17, e é por elas que o servidor confere conflitos.
    period           ENUM('MORNING','AFTERNOON','NIGHT') NULL,
    start_hour       TINYINT      NOT NULL,
    end_hour         TINYINT      NOT NULL,
    unit             VARCHAR(80)  NOT NULL,
    status           ENUM('CONFIRMED','PENDING','REJECTED','CANCELED') NOT NULL,
    resident_name    VARCHAR(120) NOT NULL DEFAULT '',
    rejection_reason VARCHAR(255) NULL,
    created_at       DATETIME     NOT NULL,
    KEY ix_reservations_slot (area_id, date),
    KEY ix_reservations_unit (condo_id, unit),
    CONSTRAINT fk_reservations_area FOREIGN KEY (area_id) REFERENCES common_areas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_bookings (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id         VARCHAR(36)  NOT NULL,
    type             ENUM('MOVE','REPAIR') NOT NULL,
    date             DATE         NOT NULL,
    period           ENUM('MORNING','AFTERNOON','NIGHT') NOT NULL,
    unit             VARCHAR(80)  NOT NULL,
    resident_name    VARCHAR(120) NOT NULL DEFAULT '',
    status           ENUM('CONFIRMED','PENDING','REJECTED','CANCELED') NOT NULL,
    repair_kind      ENUM('INTERNET','ELECTRICAL','PLUMBING','OTHER') NULL,
    provider         VARCHAR(120) NOT NULL DEFAULT '',
    rejection_reason VARCHAR(255) NULL,
    created_at       DATETIME     NOT NULL,
    KEY ix_bookings_condo (condo_id, date),
    CONSTRAINT fk_bookings_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- "Vencido" não é guardado: é um boleto OPEN com vencimento no passado.
CREATE TABLE IF NOT EXISTS bills (
    id        VARCHAR(36)   NOT NULL PRIMARY KEY,
    condo_id  VARCHAR(36)   NOT NULL,
    unit      VARCHAR(80)   NOT NULL,
    reference CHAR(7)       NOT NULL,
    amount    DECIMAL(10,2) NOT NULL,
    due_date  DATE          NOT NULL,
    status    ENUM('OPEN','PAID') NOT NULL DEFAULT 'OPEN',
    barcode   VARCHAR(80)   NOT NULL,
    paid_at   DATE          NULL,
    UNIQUE KEY uq_bills_unit_ref (condo_id, unit, reference),
    CONSTRAINT fk_bills_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Um PDF de boleto por unidade e mês: o novo substitui o anterior.
CREATE TABLE IF NOT EXISTS unit_documents (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id    VARCHAR(36)  NOT NULL,
    unit        VARCHAR(80)  NOT NULL,
    reference   CHAR(7)      NOT NULL,
    file_id     VARCHAR(36)  NOT NULL,
    uploaded_at DATETIME     NOT NULL,
    UNIQUE KEY uq_unit_documents (condo_id, unit, reference),
    CONSTRAINT fk_unit_documents_file FOREIGN KEY (file_id) REFERENCES files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS parcels (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id      VARCHAR(36)  NOT NULL,
    unit          VARCHAR(80)  NOT NULL,
    description   VARCHAR(160) NOT NULL,
    carrier       VARCHAR(80)  NOT NULL DEFAULT '',
    received_by   VARCHAR(120) NOT NULL,
    arrived_at    DATETIME     NOT NULL,
    pickup_code   CHAR(4)      NOT NULL,
    status        ENUM('WAITING','PICKED_UP') NOT NULL DEFAULT 'WAITING',
    picked_up_at  DATETIME     NULL,
    photo_file_id VARCHAR(36)  NULL,
    delivered_by  VARCHAR(120) NULL,
    KEY ix_parcels_unit (condo_id, unit),
    CONSTRAINT fk_parcels_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- seq gera o protocolo (OC-1041, OC-1042...).
CREATE TABLE IF NOT EXISTS occurrences (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    seq         INT          NOT NULL AUTO_INCREMENT,
    condo_id    VARCHAR(36)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    category    ENUM('NOISE','MAINTENANCE','SECURITY','GARAGE','CLEANING','OTHER') NOT NULL,
    status      ENUM('OPEN','IN_REVIEW','NEEDS_INFO','RESOLVED') NOT NULL DEFAULT 'OPEN',
    created_at  DATETIME     NOT NULL,
    unit        VARCHAR(80)  NOT NULL,
    author_name VARCHAR(120) NOT NULL DEFAULT '',
    photo_ids   JSON         NOT NULL,
    UNIQUE KEY uq_occurrences_seq (seq),
    KEY ix_occurrences_unit (condo_id, unit),
    CONSTRAINT fk_occurrences_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1041 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS occurrence_messages (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    occurrence_id   VARCHAR(36)  NOT NULL,
    author          VARCHAR(160) NOT NULL,
    from_management TINYINT(1)   NOT NULL,
    text            TEXT         NOT NULL,
    sent_at         DATETIME     NOT NULL,
    photo_ids       JSON         NOT NULL,
    CONSTRAINT fk_messages_occurrence FOREIGN KEY (occurrence_id) REFERENCES occurrences(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS condo_documents (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    condo_id    VARCHAR(36)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    category    ENUM('BYLAWS','CONVENTION','MINUTES','OTHER') NOT NULL,
    file_id     VARCHAR(36)  NULL,
    file_name   VARCHAR(255) NOT NULL,
    size_bytes  BIGINT       NOT NULL DEFAULT 0,
    uploaded_at DATETIME     NOT NULL,
    uploaded_by VARCHAR(160) NOT NULL,
    KEY ix_documents_condo (condo_id),
    CONSTRAINT fk_documents_condo FOREIGN KEY (condo_id) REFERENCES condominiums(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
