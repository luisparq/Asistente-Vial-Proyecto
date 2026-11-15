package com.avigem.diagnostics.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_summary")
public class DiagnosticSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    // Acelerómetro X
    @Column(name = "ax_min") private Short axMin;
    @Column(name = "ax_max") private Short axMax;
    @Column(name = "ax_avg") private Double axAvg;
    @Column(name = "ax_std") private Double axStd;

    // Acelerómetro Y
    @Column(name = "ay_min") private Short ayMin;
    @Column(name = "ay_max") private Short ayMax;
    @Column(name = "ay_avg") private Double ayAvg;
    @Column(name = "ay_std") private Double ayStd;

    // Acelerómetro Z
    @Column(name = "az_min") private Short azMin;
    @Column(name = "az_max") private Short azMax;
    @Column(name = "az_avg") private Double azAvg;
    @Column(name = "az_std") private Double azStd;

    // Giroscopio X
    @Column(name = "gx_min") private Short gxMin;
    @Column(name = "gx_max") private Short gxMax;
    @Column(name = "gx_avg") private Double gxAvg;
    @Column(name = "gx_std") private Double gxStd;

    // Giroscopio Y
    @Column(name = "gy_min") private Short gyMin;
    @Column(name = "gy_max") private Short gyMax;
    @Column(name = "gy_avg") private Double gyAvg;
    @Column(name = "gy_std") private Double gyStd;

    // Giroscopio Z
    @Column(name = "gz_min") private Short gzMin;
    @Column(name = "gz_max") private Short gzMax;
    @Column(name = "gz_avg") private Double gzAvg;
    @Column(name = "gz_std") private Double gzStd;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public DiagnosticSummaryEntity() {}

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    public Short getAxMin() { return axMin; }
    public void setAxMin(Short axMin) { this.axMin = axMin; }
    public Short getAxMax() { return axMax; }
    public void setAxMax(Short axMax) { this.axMax = axMax; }
    public Double getAxAvg() { return axAvg; }
    public void setAxAvg(Double axAvg) { this.axAvg = axAvg; }
    public Double getAxStd() { return axStd; }
    public void setAxStd(Double axStd) { this.axStd = axStd; }
    public Short getAyMin() { return ayMin; }
    public void setAyMin(Short ayMin) { this.ayMin = ayMin; }
    public Short getAyMax() { return ayMax; }
    public void setAyMax(Short ayMax) { this.ayMax = ayMax; }
    public Double getAyAvg() { return ayAvg; }
    public void setAyAvg(Double ayAvg) { this.ayAvg = ayAvg; }
    public Double getAyStd() { return ayStd; }
    public void setAyStd(Double ayStd) { this.ayStd = ayStd; }
    public Short getAzMin() { return azMin; }
    public void setAzMin(Short azMin) { this.azMin = azMin; }
    public Short getAzMax() { return azMax; }
    public void setAzMax(Short azMax) { this.azMax = azMax; }
    public Double getAzAvg() { return azAvg; }
    public void setAzAvg(Double azAvg) { this.azAvg = azAvg; }
    public Double getAzStd() { return azStd; }
    public void setAzStd(Double azStd) { this.azStd = azStd; }
    public Short getGxMin() { return gxMin; }
    public void setGxMin(Short gxMin) { this.gxMin = gxMin; }
    public Short getGxMax() { return gxMax; }
    public void setGxMax(Short gxMax) { this.gxMax = gxMax; }
    public Double getGxAvg() { return gxAvg; }
    public void setGxAvg(Double gxAvg) { this.gxAvg = gxAvg; }
    public Double getGxStd() { return gxStd; }
    public void setGxStd(Double gxStd) { this.gxStd = gxStd; }
    public Short getGyMin() { return gyMin; }
    public void setGyMin(Short gyMin) { this.gyMin = gyMin; }
    public Short getGyMax() { return gyMax; }
    public void setGyMax(Short gyMax) { this.gyMax = gyMax; }
    public Double getGyAvg() { return gyAvg; }
    public void setGyAvg(Double gyAvg) { this.gyAvg = gyAvg; }
    public Double getGyStd() { return gyStd; }
    public void setGyStd(Double gyStd) { this.gyStd = gyStd; }
    public Short getGzMin() { return gzMin; }
    public void setGzMin(Short gzMin) { this.gzMin = gzMin; }
    public Short getGzMax() { return gzMax; }
    public void setGzMax(Short gzMax) { this.gzMax = gzMax; }
    public Double getGzAvg() { return gzAvg; }
    public void setGzAvg(Double gzAvg) { this.gzAvg = gzAvg; }
    public Double getGzStd() { return gzStd; }
    public void setGzStd(Double gzStd) { this.gzStd = gzStd; }
    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
}
