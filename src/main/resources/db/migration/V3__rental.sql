-- ============================================================
-- Bounded context: RENTAL
-- ============================================================
CREATE TABLE reservations (
    id             BINARY(16)   NOT NULL,
    code           CHAR(10)     NOT NULL,         -- base32 Crockford, para el usuario
    client_id      BINARY(16)   NOT NULL,
    provider_id    BINARY(16)   NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,         -- inclusivo
    status         VARCHAR(20)  NOT NULL,
    status_reason  VARCHAR(300) NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_reservations          PRIMARY KEY (id),
    CONSTRAINT uq_reservations_code     UNIQUE (code),
    CONSTRAINT fk_reservations_client   FOREIGN KEY (client_id)   REFERENCES users (id),
    CONSTRAINT fk_reservations_provider FOREIGN KEY (provider_id) REFERENCES users (id),
    CONSTRAINT ck_reservations_dates    CHECK (end_date >= start_date),
    CONSTRAINT ck_reservations_parties  CHECK (client_id <> provider_id),
    CONSTRAINT ck_reservations_status   CHECK (status IN ('PENDING', 'CONFIRMED', 'READY_FOR_PICKUP',
                                                          'ACTIVE', 'RETURNED', 'CLOSED',
                                                          'REJECTED', 'CANCELLED'))
);
CREATE INDEX ix_reservations_client   ON reservations (client_id, status);
CREATE INDEX ix_reservations_provider ON reservations (provider_id, status);
CREATE INDEX ix_reservations_dates    ON reservations (start_date, end_date);

CREATE TABLE reservation_units (
    id              BINARY(16)    NOT NULL,
    reservation_id  BINARY(16)    NOT NULL,
    unit_id         BINARY(16)    NOT NULL,
    daily_rate      DECIMAL(12,2) NOT NULL,   -- tarifa diaria pactada (base de la mora)
    agreed_amount   DECIMAL(12,2) NOT NULL,   -- importe pactado por el periodo completo
    deposit_amount  DECIMAL(12,2) NOT NULL,   -- garantía pactada por esta unidad
    CONSTRAINT pk_reservation_units             PRIMARY KEY (id),
    CONSTRAINT uq_reservation_units_res_unit    UNIQUE (reservation_id, unit_id),
    CONSTRAINT fk_reservation_units_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE,
    CONSTRAINT fk_reservation_units_unit        FOREIGN KEY (unit_id)        REFERENCES tool_units (id),
    CONSTRAINT ck_reservation_units_amounts     CHECK (daily_rate > 0 AND agreed_amount > 0 AND deposit_amount >= 0)
);
CREATE INDEX ix_reservation_units_unit ON reservation_units (unit_id);

CREATE TABLE checklist_items (
    id      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code    VARCHAR(40)       NOT NULL,
    label   VARCHAR(120)      NOT NULL,
    active  BOOLEAN           NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_checklist_items      PRIMARY KEY (id),
    CONSTRAINT uq_checklist_items_code UNIQUE (code)
);

INSERT INTO checklist_items (code, label) VALUES
    ('CARCASA',        'Carcasa / estructura sin golpes ni fisuras'),
    ('CABLE_ENCHUFE',  'Cable, enchufe o batería en buen estado'),
    ('FUNCIONAMIENTO', 'Enciende y opera correctamente'),
    ('ACCESORIOS',     'Accesorios completos'),
    ('SEGURIDAD',      'Protecciones y dispositivos de seguridad'),
    ('LIMPIEZA',       'Limpieza general');

CREATE TABLE inspections (
    id                   BINARY(16)  NOT NULL,
    reservation_unit_id  BINARY(16)  NOT NULL,
    type                 VARCHAR(10) NOT NULL,
    inspector_id         BINARY(16)  NOT NULL,
    performed_at         DATETIME(6) NOT NULL,   -- hace de fecha de entrega / devolución
    overall_condition    VARCHAR(20) NOT NULL,
    observations         TEXT        NULL,
    CONSTRAINT pk_inspections           PRIMARY KEY (id),
    CONSTRAINT uq_inspections_unit_type UNIQUE (reservation_unit_id, type),
    CONSTRAINT fk_inspections_res_unit  FOREIGN KEY (reservation_unit_id) REFERENCES reservation_units (id),
    CONSTRAINT fk_inspections_inspector FOREIGN KEY (inspector_id)        REFERENCES users (id),
    CONSTRAINT ck_inspections_type      CHECK (type IN ('DELIVERY', 'RETURN')),
    CONSTRAINT ck_inspections_condition CHECK (overall_condition IN ('GOOD', 'MINOR_DAMAGE', 'MAJOR_DAMAGE', 'LOST')),
    CONSTRAINT ck_inspections_lost      CHECK (overall_condition <> 'LOST' OR type = 'RETURN')
);

CREATE TABLE inspection_results (
    inspection_id      BINARY(16)        NOT NULL,
    checklist_item_id  SMALLINT UNSIGNED NOT NULL,
    result             VARCHAR(15)       NOT NULL,
    note               VARCHAR(300)      NULL,
    CONSTRAINT pk_inspection_results            PRIMARY KEY (inspection_id, checklist_item_id),
    CONSTRAINT fk_inspection_results_inspection FOREIGN KEY (inspection_id)     REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT fk_inspection_results_item       FOREIGN KEY (checklist_item_id) REFERENCES checklist_items (id),
    CONSTRAINT ck_inspection_results_result     CHECK (result IN ('OK', 'DAMAGED', 'MISSING', 'NOT_APPLICABLE'))
);

CREATE TABLE inspection_photos (
    id             BINARY(16)   NOT NULL,
    inspection_id  BINARY(16)   NOT NULL,
    storage_key    VARCHAR(255) NOT NULL,
    content_type   VARCHAR(30)  NOT NULL,
    size_bytes     INT UNSIGNED NOT NULL,
    uploaded_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_inspection_photos            PRIMARY KEY (id),
    CONSTRAINT uq_inspection_photos_key        UNIQUE (storage_key),
    CONSTRAINT fk_inspection_photos_inspection FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT ck_inspection_photos_type       CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_inspection_photos_size       CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);
