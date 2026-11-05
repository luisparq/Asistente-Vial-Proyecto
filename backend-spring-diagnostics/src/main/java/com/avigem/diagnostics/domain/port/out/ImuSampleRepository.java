package com.avigem.diagnostics.domain.port.out;

import com.avigem.diagnostics.domain.model.ImuSample;
import java.util.List;

/**
 * Puerto de salida: persistencia de muestras IMU.
 */
public interface ImuSampleRepository {
    List<ImuSample> saveAll(List<ImuSample> samples);
    List<ImuSample> findBySessionId(Long sessionId);
    long countBySessionId(Long sessionId);
}
