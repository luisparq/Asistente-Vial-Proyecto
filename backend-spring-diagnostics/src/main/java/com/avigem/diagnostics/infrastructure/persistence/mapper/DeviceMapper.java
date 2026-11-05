package com.avigem.diagnostics.infrastructure.persistence.mapper;

import com.avigem.diagnostics.domain.model.Device;
import com.avigem.diagnostics.infrastructure.persistence.entity.DeviceEntity;
import org.springframework.stereotype.Component;

@Component
public class DeviceMapper {

    public Device toDomain(DeviceEntity e) {
        return new Device(e.getId(), e.getDeviceCode(), e.getDescription(), e.getRegisteredAt());
    }

    public DeviceEntity toEntity(Device d) {
        DeviceEntity e = new DeviceEntity();
        e.setId(d.getId());
        e.setDeviceCode(d.getDeviceCode());
        e.setDescription(d.getDescription());
        e.setRegisteredAt(d.getRegisteredAt());
        return e;
    }
}
