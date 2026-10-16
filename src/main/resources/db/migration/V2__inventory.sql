-- ============================================================
-- Bounded context: INVENTORY
-- ============================================================
CREATE TABLE categories (
    id           BINARY(16)   NOT NULL,
    parent_id    BINARY(16)   NULL,
    name         VARCHAR(80)  NOT NULL,
    description  VARCHAR(500) NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_categories        PRIMARY KEY (id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id),
    CONSTRAINT ck_categories_name   CHECK (CHAR_LENGTH(TRIM(name)) > 0)
);
-- Nombre único entre categorías hermanas. Las raíces (parent_id NULL) se normalizan a un valor
-- centinela dentro del índice funcional porque UNIQUE trata cada NULL como distinto.
-- El centinela no se guarda como dato.
CREATE UNIQUE INDEX uq_categories_parent_name
    ON categories ((COALESCE(parent_id, 0x00000000000000000000000000000000)), name);

CREATE TABLE tools (
    id              BINARY(16)    NOT NULL,
    provider_id     BINARY(16)    NOT NULL,
    name            VARCHAR(150)  NOT NULL,
    brand           VARCHAR(80)   NULL,
    model           VARCHAR(80)   NULL,
    description     TEXT          NULL,
    deposit_amount  DECIMAL(12,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_tools               PRIMARY KEY (id),
    CONSTRAINT uq_tools_provider_name UNIQUE (provider_id, name),
    CONSTRAINT fk_tools_provider      FOREIGN KEY (provider_id) REFERENCES users (id),
    CONSTRAINT ck_tools_deposit       CHECK (deposit_amount >= 0),
    CONSTRAINT ck_tools_status        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_tools_status ON tools (status);
CREATE FULLTEXT INDEX ft_tools_search ON tools (name, brand, model, description);

CREATE TABLE tool_categories (
    tool_id      BINARY(16) NOT NULL,
    category_id  BINARY(16) NOT NULL,
    CONSTRAINT pk_tool_categories          PRIMARY KEY (tool_id, category_id),
    CONSTRAINT fk_tool_categories_tool     FOREIGN KEY (tool_id)     REFERENCES tools (id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_categories_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE TABLE tool_rates (
    tool_id  BINARY(16)    NOT NULL,
    period   VARCHAR(10)   NOT NULL,
    price    DECIMAL(12,2) NOT NULL,
    CONSTRAINT pk_tool_rates        PRIMARY KEY (tool_id, period),
    CONSTRAINT fk_tool_rates_tool   FOREIGN KEY (tool_id) REFERENCES tools (id) ON DELETE CASCADE,
    CONSTRAINT ck_tool_rates_period CHECK (period IN ('DAY', 'WEEK', 'MONTH')),
    CONSTRAINT ck_tool_rates_price  CHECK (price > 0)
);

CREATE TABLE tool_images (
    id            BINARY(16)        NOT NULL,
    tool_id       BINARY(16)        NOT NULL,
    storage_key   VARCHAR(255)      NOT NULL,
    content_type  VARCHAR(30)       NOT NULL,
    size_bytes    INT UNSIGNED      NOT NULL,
    position      SMALLINT UNSIGNED NOT NULL,   -- 0 = portada
    uploaded_at   DATETIME(6)       NOT NULL,
    CONSTRAINT pk_tool_images               PRIMARY KEY (id),
    CONSTRAINT uq_tool_images_storage_key   UNIQUE (storage_key),
    CONSTRAINT uq_tool_images_tool_position UNIQUE (tool_id, position),
    CONSTRAINT fk_tool_images_tool          FOREIGN KEY (tool_id) REFERENCES tools (id) ON DELETE CASCADE,
    CONSTRAINT ck_tool_images_type          CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_tool_images_size          CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);

CREATE TABLE tool_units (
    id             BINARY(16)   NOT NULL,
    tool_id        BINARY(16)   NOT NULL,
    serial_number  VARCHAR(80)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    acquired_on    DATE         NULL,
    notes          VARCHAR(500) NULL,
    created_at     DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tool_units             PRIMARY KEY (id),
    CONSTRAINT uq_tool_units_tool_serial UNIQUE (tool_id, serial_number),
    CONSTRAINT fk_tool_units_tool        FOREIGN KEY (tool_id) REFERENCES tools (id),
    CONSTRAINT ck_tool_units_status      CHECK (status IN ('AVAILABLE', 'MAINTENANCE', 'RETIRED'))
);
CREATE INDEX ix_tool_units_tool_status ON tool_units (tool_id, status);
