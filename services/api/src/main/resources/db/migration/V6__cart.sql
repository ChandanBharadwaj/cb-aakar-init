-- One cart per identity (user or guest); guest carts merge into the user's cart on sign-in.
CREATE TABLE carts (
    id          uuid         PRIMARY KEY,
    user_id     uuid,
    guest_id    uuid,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT carts_one_owner_ck CHECK ((user_id IS NULL) <> (guest_id IS NULL))
);
CREATE UNIQUE INDEX carts_user_uq ON carts (user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX carts_guest_uq ON carts (guest_id) WHERE guest_id IS NOT NULL;

CREATE TABLE cart_items (
    id              uuid         PRIMARY KEY,
    cart_id         uuid         NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    version_id      uuid         NOT NULL,
    material_id     varchar(80)  NOT NULL,
    qty             integer      NOT NULL CHECK (qty BETWEEN 1 AND 20),
    unit_price      jsonb        NOT NULL,
    policy_version  varchar(60)  NOT NULL,
    added_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT cart_items_cart_version_material_uq UNIQUE (cart_id, version_id, material_id)
);
