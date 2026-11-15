package com.avigem.diagnostics.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Lote idempotente de cuentas RAW recibidas por Android desde BLE. */
public class RealBleBatchRequest {
    @NotBlank @Size(max = 64)
    private String deviceCode;
    @NotNull
    private Instant startedAt;
    @NotEmpty @Size(max = 200) @Valid
    private List<@NotNull SampleItem> samples;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public List<SampleItem> getSamples() { return samples; }
    public void setSamples(List<SampleItem> samples) { this.samples = samples; }

    public static class SampleItem {
        @NotNull @PositiveOrZero private Long sequence;
        @NotNull private Instant sampledAt;
        @NotNull private Integer axRaw;
        @NotNull private Integer ayRaw;
        @NotNull private Integer azRaw;
        @NotNull private Integer gxRaw;
        @NotNull private Integer gyRaw;
        @NotNull private Integer gzRaw;

        public Long getSequence() { return sequence; }
        public void setSequence(Long sequence) { this.sequence = sequence; }
        public Instant getSampledAt() { return sampledAt; }
        public void setSampledAt(Instant sampledAt) { this.sampledAt = sampledAt; }
        public Integer getAxRaw() { return axRaw; }
        public void setAxRaw(Integer axRaw) { this.axRaw = axRaw; }
        public Integer getAyRaw() { return ayRaw; }
        public void setAyRaw(Integer ayRaw) { this.ayRaw = ayRaw; }
        public Integer getAzRaw() { return azRaw; }
        public void setAzRaw(Integer azRaw) { this.azRaw = azRaw; }
        public Integer getGxRaw() { return gxRaw; }
        public void setGxRaw(Integer gxRaw) { this.gxRaw = gxRaw; }
        public Integer getGyRaw() { return gyRaw; }
        public void setGyRaw(Integer gyRaw) { this.gyRaw = gyRaw; }
        public Integer getGzRaw() { return gzRaw; }
        public void setGzRaw(Integer gzRaw) { this.gzRaw = gzRaw; }
    }
}
