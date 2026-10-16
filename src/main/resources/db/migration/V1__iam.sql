-- ============================================================
-- Bounded context: IAM
-- ============================================================
CREATE TABLE roles (
    code         VARCHAR(20)  NOT NULL,
    description  VARCHAR(100) NOT NULL,
    CONSTRAINT pk_roles      PRIMARY KEY (code),
    CONSTRAINT ck_roles_code CHECK (code IN ('ADMIN', 'PROVEEDOR', 'CLIENTE'))
);

INSERT INTO roles (code, description) VALUES
    ('ADMIN',     'Administrador de la plataforma'),
    ('PROVEEDOR', 'Proveedor que publica herramientas'),
    ('CLIENTE',   'Cliente que alquila herramientas');

CREATE TABLE users (
    id             BINARY(16)   NOT NULL,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(150) NOT NULL,
    phone          VARCHAR(20)  NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT uq_users_email  UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE user_roles (
    user_id    BINARY(16)  NOT NULL,
    role_code  VARCHAR(20) NOT NULL,
    CONSTRAINT pk_user_roles      PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)   REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_code) REFERENCES roles (code)
);

CREATE TABLE provider_profiles (
    user_id        BINARY(16)   NOT NULL,
    business_name  VARCHAR(150) NOT NULL,
    tax_id         VARCHAR(20)  NOT NULL,
    approved_at    DATETIME(6)  NULL,
    approved_by    BINARY(16)   NULL,
    CONSTRAINT pk_provider_profiles          PRIMARY KEY (user_id),
    CONSTRAINT uq_provider_profiles_tax_id   UNIQUE (tax_id),
    CONSTRAINT fk_provider_profiles_user     FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT fk_provider_profiles_approver FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT ck_provider_profiles_approval CHECK ((approved_at IS NULL) = (approved_by IS NULL))
);

CREATE TABLE refresh_tokens (
    id          BINARY(16)  NOT NULL,
    user_id     BINARY(16)  NOT NULL,
    token_hash  CHAR(64)    NOT NULL,          -- SHA-256 en hex; el token en claro nunca se guarda
    issued_at   DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6) NULL,
    CONSTRAINT pk_refresh_tokens      PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_refresh_tokens_exp  CHECK (expires_at > issued_at)
);
