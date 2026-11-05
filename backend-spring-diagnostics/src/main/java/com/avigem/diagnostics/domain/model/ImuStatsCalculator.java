package com.avigem.diagnostics.domain.model;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Servicio de dominio: cálculos estadísticos sobre muestras IMU RAW.
 *
 * Usa desviación estándar poblacional (dividir por N).
 * No aplica escalas ni conversiones de unidades físicas.
 */
public final class ImuStatsCalculator {

    private ImuStatsCalculator() {}

    /**
     * Calcula estadísticas de un eje dado un extractor de valores.
     *
     * @param samples    lista de muestras
     * @param extractor  función que extrae el valor int de la muestra
     * @return AxisStats con min, max, avg y std poblacional
     * @throws IllegalArgumentException si la lista está vacía
     */
    public static AxisStats computeAxis(List<ImuSample> samples,
                                        ToIntFunction<ImuSample> extractor) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("La lista de muestras no puede ser vacía");
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;

        for (ImuSample s : samples) {
            int v = extractor.applyAsInt(s);
            if (v < min) min = v;
            if (v > max) max = v;
            sum += v;
        }

        double avg = (double) sum / samples.size();

        double varianceSum = 0;
        for (ImuSample s : samples) {
            double diff = extractor.applyAsInt(s) - avg;
            varianceSum += diff * diff;
        }
        double std = Math.sqrt(varianceSum / samples.size());

        return new AxisStats((short) min, (short) max, avg, std);
    }
}
