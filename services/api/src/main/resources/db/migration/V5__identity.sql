-- Phase 1 identity (PLAN §6 identity, ADR-0013): phone OTP sign-in, JWT sessions, addresses, guest ownership of designs.
CREATE TABLE users (
    id          uuid         PRIMARY KEY,
    phone       varchar(16)  NOT NULL UNIQUE,
    name        varchar(80),
    email       varchar(254),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE otp_requests (
    id           uuid         PRIMARY KEY,
    phone        varchar(16)  NOT NULL,
    code_hash    varchar(64)  NOT NULL,
    attempts     integer      NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    expires_at   timestamptz  NOT NULL,
    verified_at  timestamptz
);
CREATE INDEX otp_requests_phone_created_idx ON otp_requests (phone, created_at);

-- One row per issued access token; id is the token's jti. Logout sets revoked_at.
CREATE TABLE sessions (
    id          uuid         PRIMARY KEY,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz
);
CREATE INDEX sessions_user_idx ON sessions (user_id);

CREATE TABLE addresses (
    id          uuid         PRIMARY KEY,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label       varchar(30),
    name        varchar(80)  NOT NULL,
    phone       varchar(20)  NOT NULL,
    line1       varchar(120) NOT NULL,
    line2       varchar(120),
    city        varchar(60)  NOT NULL,
    state       varchar(60)  NOT NULL,
    pincode     varchar(6)   NOT NULL,
    is_default  boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX addresses_user_idx ON addresses (user_id);

-- Designs started by a guest carry the browser's guest id until sign-in moves them to owner_id.
ALTER TABLE designs ADD COLUMN guest_id uuid;
CREATE INDEX designs_guest_idx ON designs (guest_id) WHERE guest_id IS NOT NULL;
