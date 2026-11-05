package com.avigem.diagnostics.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request para registrar un nuevo dispositivo.
 */
public class RegisterDeviceRequest {

    @NotBlank(message = "deviceCode es obligatorio")
    @Size(max = 64, message = "deviceCode no puede superar 64 caracteres")
    private String deviceCode;

    @Size(max = 255, message = "description no puede superar 255 caracteres")
    private String description;

    public RegisterDeviceRequest() {}

    public RegisterDeviceRequest(String deviceCode, String description) {
        this.deviceCode = deviceCode;
        this.description = description;
    }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
