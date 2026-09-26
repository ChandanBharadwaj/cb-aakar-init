-- Aakar storefront API · Phase 0 schema (PLAN §9). JSONB where the shape moves fast, relational elsewhere.

CREATE TABLE catalog_items (
    slug              varchar(80)  PRIMARY KEY,
    name              varchar(120) NOT NULL,
    category          varchar(40)  NOT NULL,
    description       text,
    template_id       varchar(80)  NOT NULL,
    default_params    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    default_material  varchar(80)  NOT NULL,
    base_price_paise  bigint       NOT NULL CHECK (base_price_paise >= 0),
    specs_line        varchar(200),
    environment       varchar(40),
    available         boolean      NOT NULL DEFAULT false,
    media             jsonb        NOT NULL DEFAULT '[]'::jsonb,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX catalog_items_category_idx ON catalog_items (category);

CREATE TABLE materials (
    id                varchar(80)  PRIMARY KEY,
    name              varchar(120) NOT NULL,
    filament          varchar(200) NOT NULL,
    density_g_cm3     numeric(6,3) NOT NULL CHECK (density_g_cm3 > 0),
    finish_class      varchar(20)  NOT NULL CHECK (finish_class IN ('matte', 'silk')),
    rate_per_g_paise  bigint       NOT NULL CHECK (rate_per_g_paise >= 0),
    heat_safe         boolean      NOT NULL DEFAULT false,
    pbr               jsonb        NOT NULL,
    sort_order        integer      NOT NULL DEFAULT 0
);

CREATE TABLE designs (
    id                 uuid         PRIMARY KEY,
    owner_id           uuid,
    source             varchar(20)  NOT NULL CHECK (source IN ('shop', 'create', 'remix')),
    catalog_item_slug  varchar(80)  REFERENCES catalog_items (slug),
    title              varchar(120) NOT NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX designs_owner_idx ON designs (owner_id);

CREATE TABLE design_versions (
    id                 uuid         PRIMARY KEY,
    design_id          uuid         NOT NULL REFERENCES designs (id) ON DELETE CASCADE,
    version_no         integer      NOT NULL CHECK (version_no >= 1),
    parent_version_id  uuid         REFERENCES design_versions (id),
    status             varchar(20)  NOT NULL CHECK (status IN ('generating', 'ready', 'failed')),
    spec               jsonb        NOT NULL,
    template           jsonb,
    assets             jsonb,
    geometry           jsonb,
    printability       jsonb,
    print_estimate     jsonb,
    karigar_note       text,
    job_id             uuid,
    created_by         varchar(20)  NOT NULL CHECK (created_by IN ('user', 'agent', 'system')),
    created_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT design_versions_design_version_uq UNIQUE (design_id, version_no)
);
CREATE INDEX design_versions_spec_gin ON design_versions USING gin (spec);
CREATE INDEX design_versions_job_idx ON design_versions (job_id);

CREATE TABLE generation_jobs (
    id           uuid         PRIMARY KEY,
    design_id    uuid         NOT NULL,
    version_id   uuid,
    version_no   integer      NOT NULL,
    type         varchar(20)  NOT NULL DEFAULT 'generate',
    status       varchar(20)  NOT NULL CHECK (status IN ('queued', 'running', 'succeeded', 'failed')),
    stage        varchar(20)  NOT NULL CHECK (stage IN ('queued', 'understanding', 'sculpting', 'checking', 'pricing', 'ready', 'failed')),
    message      text,
    error_code   varchar(40),
    attempts     integer      NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    started_at   timestamptz,
    finished_at  timestamptz
);
CREATE INDEX generation_jobs_design_idx ON generation_jobs (design_id);
CREATE INDEX generation_jobs_active_idx ON generation_jobs (status) WHERE status IN ('queued', 'running');

CREATE TABLE job_events (
    id               bigserial    PRIMARY KEY,
    job_id           uuid         NOT NULL REFERENCES generation_jobs (id) ON DELETE CASCADE,
    sequence         integer      NOT NULL CHECK (sequence >= 1),
    stage            varchar(20)  NOT NULL,
    message          text         NOT NULL,
    percent          integer      CHECK (percent BETWEEN 0 AND 100),
    version_id       uuid,
    error_code       varchar(40),
    at               timestamptz  NOT NULL DEFAULT now(),
    source_event_id  uuid,
    CONSTRAINT job_events_job_sequence_uq UNIQUE (job_id, sequence)
);
CREATE UNIQUE INDEX job_events_source_event_uq ON job_events (job_id, source_event_id) WHERE source_event_id IS NOT NULL;

CREATE TABLE outbox_events (
    id              bigserial    PRIMARY KEY,
    aggregate_type  varchar(40)  NOT NULL,
    aggregate_id    varchar(80)  NOT NULL,
    type            varchar(60)  NOT NULL,
    payload         jsonb        NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    published_at    timestamptz
);
CREATE INDEX outbox_events_unpublished_idx ON outbox_events (created_at) WHERE published_at IS NULL;
