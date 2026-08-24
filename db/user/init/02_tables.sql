CREATE TABLE IF NOT EXISTS users (
    user_id         BIGSERIAL      PRIMARY KEY,
    email           CITEXT         NOT NULL,
    password_hash   VARCHAR(255)   NOT NULL,
    full_name       VARCHAR(150)   NOT NULL,
    phone           VARCHAR(20),
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email      UNIQUE (email),
    CONSTRAINT chk_users_email_fmt CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);
