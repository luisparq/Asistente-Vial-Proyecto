-- V4: Ingesta idempotente de muestras REAL_BLE desde Android.
-- El origen sigue siendo asignado exclusivamente por el backend.

ALTER TABLE diagnostic_session
    ADD COLUMN client_session_id VARCHAR(36);

CREATE UNIQUE INDEX uq_session_client_session
    ON diagnostic_session(client_session_id);

ALTER TABLE imu_sample
    ADD COLUMN sequence_number BIGINT;

CREATE UNIQUE INDEX uq_sample_session_sequence
    ON imu_sample(session_id, sequence_number);
