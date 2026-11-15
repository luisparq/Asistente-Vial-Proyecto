package com.avigem.diagnostics.domain.model;

import java.time.Instant;

/**
 * Modelo de dominio: Dispositivo registrado.
 */
public class Device {
    private Long id;
    private String deviceCode;
    private String description;
    private Instant registeredAt;

    public Device() {}

    public Device(Long id, String deviceCode, String description, Instant registeredAt) {
        this.id = id;
        this.deviceCode = deviceCode;
        this.description = description;
        this.registeredAt = registeredAt;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
}
