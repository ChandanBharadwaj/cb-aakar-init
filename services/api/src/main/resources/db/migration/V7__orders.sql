-- Orders, payments, shipments and notifications (PLAN §9, §12; ADR-0013). Providers are adapters;
-- these tables are shaped for the real ones so swapping the mock is not a schema change.
CREATE SEQUENCE order_number_seq START 1;
CREATE SEQUENCE invoice_number_seq START 1;

CREATE TABLE orders (
    id               uuid          PRIMARY KEY,
    number           varchar(12)   NOT NULL UNIQUE,
    user_id          uuid          NOT NULL REFERENCES users (id),
    status           varchar(20)   NOT NULL CHECK (status IN ('pending_payment', 'confirmed', 'queued', 'slicing', 'printing',
                                                              'finishing', 'qc', 'packed', 'shipped', 'delivered', 'cancelled',
                                                              'on_hold', 'reprint')),
    address          jsonb         NOT NULL,
    subtotal_paise   bigint        NOT NULL CHECK (subtotal_paise >= 0),
    shipping_paise   bigint        NOT NULL CHECK (shipping_paise >= 0),
    shipping_label   varchar(120),
    total_paise      bigint        NOT NULL CHECK (total_paise >= 0),
    policy_version   varchar(60)   NOT NULL,
    notify_whatsapp  boolean       NOT NULL DEFAULT true,
    note             varchar(200),
    eta              date,
    placed_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX orders_user_placed_idx ON orders (user_id, placed_at DESC);
CREATE INDEX orders_status_idx ON orders (status);

CREATE TABLE order_items (
    id                uuid          PRIMARY KEY,
    order_id          uuid          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    design_id         uuid          NOT NULL,
    version_id        uuid          NOT NULL,
    version_no        integer,
    title             varchar(120)  NOT NULL,
    specs_line        varchar(200),
    material_id       varchar(80)   NOT NULL,
    material_name     varchar(120),
    qty               integer       NOT NULL CHECK (qty >= 1),
    unit_price        jsonb         NOT NULL,
    line_total_paise  bigint        NOT NULL CHECK (line_total_paise >= 0),
    assets            jsonb,
    sort_order        integer       NOT NULL DEFAULT 0
);
CREATE INDEX order_items_order_idx ON order_items (order_id);

CREATE TABLE order_events (
    id        bigserial    PRIMARY KEY,
    order_id  uuid         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sequence  integer      NOT NULL CHECK (sequence >= 1),
    status    varchar(20)  NOT NULL,
    stage     varchar(20)  NOT NULL,
    message   text         NOT NULL,
    detail    jsonb,
    at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT order_events_order_sequence_uq UNIQUE (order_id, sequence)
);

CREATE TABLE payments (
    id              uuid          PRIMARY KEY,
    order_id        uuid          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    user_id         uuid          NOT NULL,
    gateway         varchar(20)   NOT NULL,
    gateway_ref     varchar(120),
    status          varchar(20)   NOT NULL CHECK (status IN ('created', 'pending', 'succeeded', 'failed', 'refunded')),
    method          varchar(20),
    amount_paise    bigint        NOT NULL CHECK (amount_paise >= 0),
    currency        char(3)       NOT NULL DEFAULT 'INR',
    pay_url         varchar(500),
    invoice_number  varchar(20)   UNIQUE,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    finished_at     timestamptz
);
CREATE INDEX payments_order_created_idx ON payments (order_id, created_at DESC);

CREATE TABLE shipments (
    id            uuid          PRIMARY KEY,
    order_id      uuid          NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
    carrier       varchar(40)   NOT NULL,
    awb           varchar(40),
    status        varchar(20)   NOT NULL CHECK (status IN ('created', 'picked_up', 'in_transit', 'out_for_delivery', 'delivered', 'returned')),
    eta           date,
    tracking_url  varchar(500),
    events        jsonb         NOT NULL DEFAULT '[]'::jsonb,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id          uuid          PRIMARY KEY,
    user_id     uuid,
    channel     varchar(20)   NOT NULL,
    template    varchar(60)   NOT NULL,
    "to"        varchar(254)  NOT NULL,
    payload     jsonb         NOT NULL DEFAULT '{}'::jsonb,
    status      varchar(20)   NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC);
