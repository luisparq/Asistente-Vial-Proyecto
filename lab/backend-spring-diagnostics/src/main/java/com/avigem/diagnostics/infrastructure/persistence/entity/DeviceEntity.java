package com.avigem.diagnostics.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device")
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, unique = true, length = 64)
    private String deviceCode;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    public DeviceEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
}
