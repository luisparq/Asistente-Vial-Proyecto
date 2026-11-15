package com.avigem.diagnostics.domain.model;

import java.time.Instant;

/**
 * Modelo de dominio: Muestra IMU de 6 ejes en valores RAW int16.
 * NOTA: Estos datos son TEST_DATA. No representan mediciones físicas reales.
 *
 * Rango válido int16: -32768 .. 32767
 * Ejes acelerómetro: ax, ay, az
 * Ejes giroscopio:   gx, gy, gz
 */
public class ImuSample {
    private Long id;
    private Long sessionId;
    private Long sequenceNumber;

    // Acelerómetro RAW (int16)
    private short axRaw;
    private short ayRaw;
    private short azRaw;

    // Giroscopio RAW (int16)
    private short gxRaw;
    private short gyRaw;
    private short gzRaw;

    private Instant sampledAt;

    public ImuSample() {}

    public ImuSample(Long id, Long sessionId,
                     short axRaw, short ayRaw, short azRaw,
                     short gxRaw, short gyRaw, short gzRaw,
                     Instant sampledAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.axRaw = axRaw;
        this.ayRaw = ayRaw;
        this.azRaw = azRaw;
        this.gxRaw = gxRaw;
        this.gyRaw = gyRaw;
        this.gzRaw = gzRaw;
        this.sampledAt = sampledAt;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public short getAxRaw() { return axRaw; }
    public void setAxRaw(short axRaw) { this.axRaw = axRaw; }

    public short getAyRaw() { return ayRaw; }
    public void setAyRaw(short ayRaw) { this.ayRaw = ayRaw; }

    public short getAzRaw() { return azRaw; }
    public void setAzRaw(short azRaw) { this.azRaw = azRaw; }

    public short getGxRaw() { return gxRaw; }
    public void setGxRaw(short gxRaw) { this.gxRaw = gxRaw; }

    public short getGyRaw() { return gyRaw; }
    public void setGyRaw(short gyRaw) { this.gyRaw = gyRaw; }

    public short getGzRaw() { return gzRaw; }
    public void setGzRaw(short gzRaw) { this.gzRaw = gzRaw; }

    public Instant getSampledAt() { return sampledAt; }
    public void setSampledAt(Instant sampledAt) { this.sampledAt = sampledAt; }
}
