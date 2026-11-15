package com.avigem.diagnostics.domain.model;

import java.time.Instant;

/**
 * Modelo de dominio: Resumen estadístico de una sesión.
 * Contiene cantidad, mín, máx, promedio y desviación estándar por eje.
 */
public class DiagnosticSummary {
    private Long id;
    private Long sessionId;
    private int sampleCount;

    // Acelerómetro
    private Short axMin; private Short axMax; private Double axAvg; private Double axStd;
    private Short ayMin; private Short ayMax; private Double ayAvg; private Double ayStd;
    private Short azMin; private Short azMax; private Double azAvg; private Double azStd;

    // Giroscopio
    private Short gxMin; private Short gxMax; private Double gxAvg; private Double gxStd;
    private Short gyMin; private Short gyMax; private Double gyAvg; private Double gyStd;
    private Short gzMin; private Short gzMax; private Double gzAvg; private Double gzStd;

    private Instant computedAt;

    public DiagnosticSummary() {}

    // ── Getters & Setters ──────────────────────────────────────────────────────

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
