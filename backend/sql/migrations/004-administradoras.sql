-- Administradoras: cada condomínio passa a ter dona, e o ADMIN só vê os da sua.
-- Banco existente: tudo o que já existe vai para uma administradora "principal",
-- para nada mudar até a plataforma separar. Perfil novo PLATFORM (nós).

CREATE TABLE IF NOT EXISTS companies (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    cnpj            VARCHAR(20)  NOT NULL DEFAULT '',
    brand_color     VARCHAR(7)   NOT NULL DEFAULT '',
    logo_file       VARCHAR(80)  NULL,
    logo_updated_at DATETIME     NULL,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO companies (id, name, created_at) VALUES ('co-principal', 'Administradora principal', NOW());

ALTER TABLE condominiums
    ADD COLUMN company_id VARCHAR(36) NULL AFTER id,
    ADD CONSTRAINT fk_condos_company FOREIGN KEY (company_id) REFERENCES companies(id);

UPDATE condominiums SET company_id = 'co-principal';

ALTER TABLE users
    MODIFY role ENUM('PLATFORM','ADMIN','MANAGER','DOORMAN','RESIDENT') NOT NULL,
    ADD COLUMN company_id VARCHAR(36) NULL AFTER condo_id,
    ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE;

UPDATE users SET company_id = 'co-principal' WHERE role = 'ADMIN'
