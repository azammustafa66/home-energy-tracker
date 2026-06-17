CREATE TABLE breached_users (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL,
    email           TEXT         NOT NULL,
    breached_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    insight_sent_at TIMESTAMP
);

CREATE INDEX idx_breached_users_unsent   ON breached_users (user_id) WHERE insight_sent_at IS NULL;
CREATE INDEX idx_breached_users_breached ON breached_users (breached_at);
