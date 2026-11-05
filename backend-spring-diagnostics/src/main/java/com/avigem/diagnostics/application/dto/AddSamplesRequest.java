package com.avigem.diagnostics.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request para ingresar un lote de muestras IMU.
 * NOTA: Todos los valores son TEST_DATA en esta etapa.
 */
public class AddSamplesRequest {

    @NotEmpty(message = "El lote debe contener al menos una muestra")
    @Size(max = 500, message = "No se pueden enviar más de 500 muestras por lote")
    @Valid
    private List<@NotNull(message = "La muestra no puede ser nula") SampleItem> samples;

    public AddSamplesRequest() {}

    public AddSamplesRequest(List<SampleItem> samples) {
        this.samples = samples;
    }

    public List<SampleItem> getSamples() { return samples; }
    public void setSamples(List<SampleItem> samples) { this.samples = samples; }

    // ── Item ──────────────────────────────────────────────────────────────────

    public static class SampleItem {

        @NotNull(message = "axRaw es obligatorio")
        private Integer axRaw;

        @NotNull(message = "ayRaw es obligatorio")
        private Integer ayRaw;

        @NotNull(message = "azRaw es obligatorio")
        private Integer azRaw;

        @NotNull(message = "gxRaw es obligatorio")
        private Integer gxRaw;

        @NotNull(message = "gyRaw es obligatorio")
        private Integer gyRaw;

        @NotNull(message = "gzRaw es obligatorio")
        private Integer gzRaw;

        /** Instante de captura. Si es null se usa el instante de ingreso. */
        private Instant sampledAt;

        public SampleItem() {}

        public SampleItem(int axRaw, int ayRaw, int azRaw,
                          int gxRaw, int gyRaw, int gzRaw, Instant sampledAt) {
            this.axRaw = axRaw;
            this.ayRaw = ayRaw;
            this.azRaw = azRaw;
            this.gxRaw = gxRaw;
            this.gyRaw = gyRaw;
            this.gzRaw = gzRaw;
            this.sampledAt = sampledAt;
        }

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

        public Instant getSampledAt() { return sampledAt; }
        public void setSampledAt(Instant sampledAt) { this.sampledAt = sampledAt; }
    }
}
