CREATE TABLE devices (
    id             UUID PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    type           VARCHAR(30)  NOT NULL,
    serial_number  VARCHAR(100) NOT NULL UNIQUE,
    user_id        UUID         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_devices_user_id ON devices (user_id);
CREATE INDEX idx_devices_type ON devices (type);
