-- V3: Origen de datos asignado exclusivamente por el backend.
-- En esta etapa solo existe TEST_DATA; el cliente no puede elegir el origen.

ALTER TABLE diagnostic_session
    ADD COLUMN data_origin VARCHAR(20) NOT NULL DEFAULT 'TEST_DATA';
