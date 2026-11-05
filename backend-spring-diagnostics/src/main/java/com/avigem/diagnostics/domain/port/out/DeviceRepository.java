package com.avigem.diagnostics.domain.port.out;

import com.avigem.diagnostics.domain.model.Device;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;

import java.util.Optional;

/**
 * Puerto de salida: persistencia de dispositivos.
 */
public interface DeviceRepository {
    Device save(Device device);
    Optional<Device> findById(Long id);
    Optional<Device> findByDeviceCode(String deviceCode);
    boolean existsByDeviceCode(String deviceCode);
    PageResult<Device> findAll(PageQuery query);
}
