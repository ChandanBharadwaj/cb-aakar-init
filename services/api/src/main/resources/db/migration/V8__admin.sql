-- Management portal (ADR-0012): staff accounts, audit log, media assets, template live flags, share codes;
-- material availability; order and rendered text on notifications; a note on pricing policy versions.

CREATE TABLE staff_accounts (
    id             uuid          PRIMARY KEY,
    email          varchar(254)  NOT NULL UNIQUE,
    name           varchar(80)   NOT NULL,
    role           varchar(20)   NOT NULL CHECK (role IN ('owner', 'studio')),
    password_hash  varchar(100)  NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now()
);
-- The seed owner (studio@aakar.local) is inserted at startup by the admin module when this table is empty,
-- with the bcrypt hash of aakar.admin.seed-password; no hash lives in SQL.

CREATE TABLE audit_log (
    id           bigserial     PRIMARY KEY,
    at           timestamptz   NOT NULL DEFAULT now(),
    staff_email  varchar(254)  NOT NULL,
    action       varchar(60)   NOT NULL,
    target       varchar(120)  NOT NULL,
    before       jsonb,
    after        jsonb
);
CREATE INDEX audit_log_at_idx ON audit_log (at DESC, id DESC);

CREATE TABLE media_assets (
    id            uuid          PRIMARY KEY,
    kind          varchar(20)   NOT NULL CHECK (kind IN ('qc_photo', 'packaging_card', 'other')),
    order_id      uuid          REFERENCES orders (id) ON DELETE SET NULL,
    key           varchar(300)  NOT NULL,
    url           varchar(500)  NOT NULL,
    content_type  varchar(120)  NOT NULL,
    bytes         bigint        NOT NULL CHECK (bytes >= 0),
    note          varchar(200),
    created_at    timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX media_assets_order_idx ON media_assets (order_id, created_at);

CREATE TABLE template_flags (
    template_id  varchar(80)   PRIMARY KEY,
    live         boolean       NOT NULL DEFAULT true,
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

-- Reprint / remix link printed on the packaging card: {aakar.web.url}/k/{code}; minted once per order.
CREATE TABLE share_codes (
    code        varchar(8)    PRIMARY KEY,
    order_id    uuid          NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
    design_id   uuid          NOT NULL,
    version_id  uuid          NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now()
);

ALTER TABLE materials
    ADD COLUMN available   boolean      NOT NULL DEFAULT true,
    ADD COLUMN updated_at  timestamptz  NOT NULL DEFAULT now();

ALTER TABLE notifications
    ADD COLUMN order_id       uuid,
    ADD COLUMN rendered_text  text;
CREATE INDEX notifications_order_idx ON notifications (order_id, created_at DESC);
CREATE INDEX notifications_created_idx ON notifications (created_at DESC);

ALTER TABLE pricing_policies ADD COLUMN note varchar(200);
