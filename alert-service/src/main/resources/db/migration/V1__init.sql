CREATE TABLE sent_alerts (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         NOT NULL,
    sent        BOOLEAN      NOT NULL DEFAULT FALSE,
    message     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sent_alerts_user_created ON sent_alerts (user_id, created_at DESC);
CREATE INDEX idx_sent_alerts_unsent ON sent_alerts (created_at) WHERE sent = FALSE;
