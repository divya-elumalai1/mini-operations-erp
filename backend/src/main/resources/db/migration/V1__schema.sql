-- Mini Operations ERP - base schema (PostgreSQL 16)
-- All quantities are NUMERIC(14,3): exact, never floating point.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'OPERATIONS_USER', 'SALES_USER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE locations (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku             VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    category_id     BIGINT       NOT NULL REFERENCES categories(id),
    unit_of_measure VARCHAR(16)  NOT NULL DEFAULT 'PCS',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Lot metadata for traceability. Inventory itself is tracked at (item, location) grain.
CREATE TABLE batches (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id    BIGINT      NOT NULL REFERENCES items(id),
    batch_code VARCHAR(40) NOT NULL,
    expires_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_batches_item_code UNIQUE (item_id, batch_code)
);

-- One row per (item, location). Available quantity is always derived:
-- available = physical_qty - reserved_qty. Never stored.
CREATE TABLE inventory (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id      BIGINT        NOT NULL REFERENCES items(id),
    location_id  BIGINT        NOT NULL REFERENCES locations(id),
    physical_qty NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (physical_qty >= 0),
    reserved_qty NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_item_location UNIQUE (item_id, location_id),
    CONSTRAINT ck_inventory_reserved_lte_physical CHECK (reserved_qty <= physical_qty)
);

CREATE INDEX idx_inventory_location ON inventory(location_id);

-- Append-only audit ledger. Every mutation of `inventory` writes exactly one row here
-- inside the same transaction as the stock change.
CREATE TABLE inventory_transactions (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id                  BIGINT        NOT NULL REFERENCES items(id),
    location_id              BIGINT        NOT NULL REFERENCES locations(id),
    batch_id                 BIGINT        REFERENCES batches(id),
    tx_type                  VARCHAR(20)   NOT NULL CHECK (tx_type IN
                             ('GOODS_RECEIPT', 'ISSUE', 'RESERVE', 'RELEASE',
                              'TRANSFER_OUT', 'TRANSFER_IN', 'ADJUSTMENT')),
    reference_type           VARCHAR(20)   NOT NULL CHECK (reference_type IN
                             ('MANUAL', 'INTERNAL_TRANSFER', 'CUSTOMER_ORDER', 'WORK_ORDER')),
    reference_id             BIGINT,
    quantity_delta_physical  NUMERIC(14,3) NOT NULL DEFAULT 0,
    quantity_delta_reserved  NUMERIC(14,3) NOT NULL DEFAULT 0,
    notes                    VARCHAR(255),
    idempotency_key          VARCHAR(64) UNIQUE,
    performed_by             BIGINT        NOT NULL REFERENCES users(id),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_inv_tx_item_loc_time ON inventory_transactions(item_id, location_id, created_at DESC);
CREATE INDEX idx_inv_tx_reference ON inventory_transactions(reference_type, reference_id);

CREATE SEQUENCE wo_number_seq  START WITH 1;
CREATE SEQUENCE trf_number_seq START WITH 1;
CREATE SEQUENCE so_number_seq  START WITH 1;

CREATE TABLE work_orders (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wo_number         VARCHAR(24)   NOT NULL UNIQUE,
    item_id           BIGINT        NOT NULL REFERENCES items(id),
    location_id       BIGINT        NOT NULL REFERENCES locations(id),
    required_quantity NUMERIC(14,3) NOT NULL CHECK (required_quantity > 0),
    assigned_user_id  BIGINT        NOT NULL REFERENCES users(id),
    status            VARCHAR(20)   NOT NULL DEFAULT 'ASSIGNED'
                      CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED')),
    completed_at      TIMESTAMPTZ,
    created_by        BIGINT        NOT NULL REFERENCES users(id),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_orders_status ON work_orders(status);

CREATE TABLE internal_transfers (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_number        VARCHAR(24)   NOT NULL UNIQUE,
    source_location_id     BIGINT        NOT NULL REFERENCES locations(id),
    destination_location_id BIGINT       NOT NULL REFERENCES locations(id),
    item_id                BIGINT        NOT NULL REFERENCES items(id),
    quantity               NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    status                 VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED'
                           CHECK (status IN ('REQUESTED', 'DISPATCHED', 'RECEIVED')),
    dispatched_by          BIGINT REFERENCES users(id),
    dispatched_at          TIMESTAMPTZ,
    received_by            BIGINT REFERENCES users(id),
    received_at            TIMESTAMPTZ,
    created_by             BIGINT        NOT NULL REFERENCES users(id),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_transfer_locations_different CHECK (source_location_id <> destination_location_id)
);

CREATE INDEX idx_transfers_status ON internal_transfers(status);

CREATE TABLE customer_orders (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_number   VARCHAR(24)  NOT NULL UNIQUE,
    customer_name  VARCHAR(120) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'CREATED'
                   CHECK (status IN ('CREATED', 'CANCELLED')),
    created_by     BIGINT       NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE customer_order_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT        NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    item_id     BIGINT        NOT NULL REFERENCES items(id),
    location_id BIGINT        NOT NULL REFERENCES locations(id),
    quantity    NUMERIC(14,3) NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_customer_order_items_order ON customer_order_items(order_id);
