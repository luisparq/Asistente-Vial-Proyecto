-- V2: Motivo de cancelación separado de las notas de la sesión.
-- No se sobrescribe diagnostic_session.notes al cancelar.

ALTER TABLE diagnostic_session
    ADD COLUMN cancellation_reason VARCHAR(500);
