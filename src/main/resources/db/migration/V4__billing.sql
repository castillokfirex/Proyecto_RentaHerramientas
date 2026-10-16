-- ============================================================
-- Bounded context: BILLING
-- ============================================================
CREATE TABLE invoices (
    id              BINARY(16)      NOT NULL,
    reservation_id  BINARY(16)      NOT NULL,
    type            VARCHAR(20)     NOT NULL,
    number          BIGINT UNSIGNED NULL,      -- se asigna al emitir (MAX + 1, protegido por el UNIQUE)
    tax_rate        DECIMAL(5,4)    NOT NULL,  -- tasa vigente al crear la factura
    issued_at       DATETIME(6)     NULL,      -- NULL = borrador
    voided_at       DATETIME(6)     NULL,      -- NOT NULL = anulada
    created_at      DATETIME(6)     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_invoices                  PRIMARY KEY (id),
    CONSTRAINT uq_invoices_number           UNIQUE (number),
    CONSTRAINT uq_invoices_reservation_type UNIQUE (reservation_id, type),
    CONSTRAINT fk_invoices_reservation      FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT ck_invoices_type             CHECK (type IN ('RENTAL', 'SETTLEMENT')),
    CONSTRAINT ck_invoices_tax              CHECK (tax_rate >= 0 AND tax_rate < 1),
    CONSTRAINT ck_invoices_issue            CHECK ((number IS NULL) = (issued_at IS NULL)),
    CONSTRAINT ck_invoices_void             CHECK (voided_at IS NULL OR (issued_at IS NOT NULL AND voided_at >= issued_at))
);

CREATE TABLE invoice_lines (
    id                   BINARY(16)    NOT NULL,
    invoice_id           BINARY(16)    NOT NULL,
    reservation_unit_id  BINARY(16)    NOT NULL,
    concept              VARCHAR(10)   NOT NULL,
    description          VARCHAR(200)  NOT NULL,
    quantity             DECIMAL(10,2) NOT NULL,
    unit_price           DECIMAL(12,2) NOT NULL,
    CONSTRAINT pk_invoice_lines              PRIMARY KEY (id),
    CONSTRAINT uq_invoice_lines_unit_concept UNIQUE (reservation_unit_id, concept),
    CONSTRAINT fk_invoice_lines_invoice      FOREIGN KEY (invoice_id)          REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT fk_invoice_lines_res_unit     FOREIGN KEY (reservation_unit_id) REFERENCES reservation_units (id),
    CONSTRAINT ck_invoice_lines_concept      CHECK (concept IN ('RENTAL', 'LATE_FEE', 'DAMAGE')),
    CONSTRAINT ck_invoice_lines_amounts      CHECK (quantity > 0 AND unit_price >= 0)
);
CREATE INDEX ix_invoice_lines_invoice ON invoice_lines (invoice_id);

CREATE TABLE payments (
    id           BINARY(16)    NOT NULL,
    invoice_id   BINARY(16)    NOT NULL,
    kind         VARCHAR(10)   NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    method       VARCHAR(20)   NOT NULL,
    reference    VARCHAR(100)  NULL,
    occurred_at  DATETIME(6)   NOT NULL,
    recorded_by  BINARY(16)    NOT NULL,
    CONSTRAINT pk_payments             PRIMARY KEY (id),
    CONSTRAINT fk_payments_invoice     FOREIGN KEY (invoice_id)  REFERENCES invoices (id),
    CONSTRAINT fk_payments_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id),
    CONSTRAINT ck_payments_kind        CHECK (kind IN ('PAYMENT', 'REFUND')),
    CONSTRAINT ck_payments_amount      CHECK (amount > 0),
    CONSTRAINT ck_payments_method      CHECK (method IN ('CASH', 'CARD', 'TRANSFER', 'SIMULATED'))
);

CREATE TABLE security_deposits (
    id               BINARY(16)    NOT NULL,
    reservation_id   BINARY(16)    NOT NULL,
    required_amount  DECIMAL(12,2) NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    settled_at       DATETIME(6)   NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_security_deposits             PRIMARY KEY (id),
    CONSTRAINT uq_security_deposits_reservation UNIQUE (reservation_id),
    CONSTRAINT fk_security_deposits_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT ck_security_deposits_amount      CHECK (required_amount >= 0)
);

CREATE TABLE deposit_transactions (
    id           BINARY(16)    NOT NULL,
    deposit_id   BINARY(16)    NOT NULL,
    kind         VARCHAR(12)   NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    method       VARCHAR(20)   NOT NULL,
    reference    VARCHAR(100)  NULL,
    occurred_at  DATETIME(6)   NOT NULL,
    recorded_by  BINARY(16)    NOT NULL,
    CONSTRAINT pk_deposit_transactions          PRIMARY KEY (id),
    CONSTRAINT fk_deposit_transactions_deposit  FOREIGN KEY (deposit_id)  REFERENCES security_deposits (id),
    CONSTRAINT fk_deposit_transactions_recorder FOREIGN KEY (recorded_by) REFERENCES users (id),
    CONSTRAINT ck_deposit_transactions_kind     CHECK (kind IN ('COLLECTION', 'REFUND')),
    CONSTRAINT ck_deposit_transactions_amount   CHECK (amount > 0),
    CONSTRAINT ck_deposit_transactions_method   CHECK (method IN ('CASH', 'CARD', 'TRANSFER', 'SIMULATED'))
);

CREATE TABLE deposit_retentions (
    invoice_id  BINARY(16)    NOT NULL,          -- factura SETTLEMENT cubierta con la garantía
    amount      DECIMAL(12,2) NOT NULL,
    applied_at  DATETIME(6)   NOT NULL,
    CONSTRAINT pk_deposit_retentions         PRIMARY KEY (invoice_id),
    CONSTRAINT fk_deposit_retentions_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT ck_deposit_retentions_amount  CHECK (amount > 0)
);
