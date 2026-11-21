ALTER TABLE users
    ALTER COLUMN energy_alert_threshold DROP NOT NULL,
    ALTER COLUMN energy_alert_threshold DROP DEFAULT;

UPDATE users SET energy_alert_threshold = NULL WHERE energy_alert_threshold = 0;
