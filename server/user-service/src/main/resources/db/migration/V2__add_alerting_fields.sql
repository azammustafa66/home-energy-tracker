ALTER TABLE users
    ADD COLUMN alerting               BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN energy_alert_threshold DOUBLE PRECISION NOT NULL DEFAULT 0;
