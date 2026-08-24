CREATE TABLE IF NOT EXISTS outbox_events (
    outbox_id      BIGSERIAL PRIMARY KEY,
    topic          VARCHAR(120)  NOT NULL,
    message_key    VARCHAR(120),
    event_type     VARCHAR(80)   NOT NULL,
    payload        JSONB         NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox_events (outbox_id)
    WHERE published_at IS NULL;
