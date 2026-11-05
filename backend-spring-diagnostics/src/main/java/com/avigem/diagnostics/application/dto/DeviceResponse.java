package com.avigem.diagnostics.application.dto;

import com.avigem.diagnostics.domain.model.Device;
import java.time.Instant;

/**
 * Respuesta de dispositivo.
 */
public class DeviceResponse {
    private Long id;
    private String deviceCode;
    private String description;
    private Instant registeredAt;

    public DeviceResponse() {}

    public static DeviceResponse from(Device d) {
        DeviceResponse r = new DeviceResponse();
        r.id = d.getId();
        r.deviceCode = d.getDeviceCode();
        r.description = d.getDescription();
        r.registeredAt = d.getRegisteredAt();
        return r;
    }

    public Long getId() { return id; }
    public String getDeviceCode() { return deviceCode; }
    public String getDescription() { return description; }
    public Instant getRegisteredAt() { return registeredAt; }
}
