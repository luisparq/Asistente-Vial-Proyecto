package com.avigem.diagnostics.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "imu_sample")
public class ImuSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "ax_raw", nullable = false)
    private short axRaw;

    @Column(name = "ay_raw", nullable = false)
    private short ayRaw;

    @Column(name = "az_raw", nullable = false)
    private short azRaw;

    @Column(name = "gx_raw", nullable = false)
    private short gxRaw;

    @Column(name = "gy_raw", nullable = false)
    private short gyRaw;

    @Column(name = "gz_raw", nullable = false)
    private short gzRaw;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    public ImuSampleEntity() {}

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
