-- V1: Esquema inicial AVIGEM Diagnostics
-- NOTA: Todos los datos son TEST_DATA en esta etapa. No representan mediciones físicas reales.

CREATE TABLE device (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    device_code     VARCHAR(64)     NOT NULL,
    description     VARCHAR(255),
    registered_at   DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_device_code UNIQUE (device_code)
);

CREATE TABLE diagnostic_session (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    device_id       BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    result          VARCHAR(20)     NOT NULL,
    notes           VARCHAR(500),
    started_at      DATETIME(6)     NOT NULL,
    finished_at     DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_session_device FOREIGN KEY (device_id) REFERENCES device(id)
);

CREATE TABLE imu_sample (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    ax_raw          SMALLINT        NOT NULL,
    ay_raw          SMALLINT        NOT NULL,
    az_raw          SMALLINT        NOT NULL,
    gx_raw          SMALLINT        NOT NULL,
    gy_raw          SMALLINT        NOT NULL,
    gz_raw          SMALLINT        NOT NULL,
    sampled_at      DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sample_session FOREIGN KEY (session_id) REFERENCES diagnostic_session(id)
);

CREATE TABLE diagnostic_summary (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    sample_count    INT             NOT NULL DEFAULT 0,
    ax_min          SMALLINT,
    ax_max          SMALLINT,
    ax_avg          DOUBLE,
    ax_std          DOUBLE,
    ay_min          SMALLINT,
    ay_max          SMALLINT,
    ay_avg          DOUBLE,
    ay_std          DOUBLE,
    az_min          SMALLINT,
    az_max          SMALLINT,
    az_avg          DOUBLE,
    az_std          DOUBLE,
    gx_min          SMALLINT,
    gx_max          SMALLINT,
    gx_avg          DOUBLE,
    gx_std          DOUBLE,
    gy_min          SMALLINT,
    gy_max          SMALLINT,
    gy_avg          DOUBLE,
    gy_std          DOUBLE,
    gz_min          SMALLINT,
    gz_max          SMALLINT,
    gz_avg          DOUBLE,
    gz_std          DOUBLE,
    computed_at     DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_summary_session UNIQUE (session_id),
    CONSTRAINT fk_summary_session FOREIGN KEY (session_id) REFERENCES diagnostic_session(id)
);

CREATE INDEX idx_session_device   ON diagnostic_session(device_id);
CREATE INDEX idx_session_status   ON diagnostic_session(status);
CREATE INDEX idx_sample_session   ON imu_sample(session_id);
CREATE INDEX idx_sample_sampled   ON imu_sample(sampled_at);
