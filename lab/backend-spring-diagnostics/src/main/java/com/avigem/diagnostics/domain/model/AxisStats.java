package com.avigem.diagnostics.domain.model;

/**
 * Estadísticas calculadas para un eje IMU RAW.
 * Población completa (desviación estándar poblacional, no muestral).
 */
public class AxisStats {
    private final short min;
    private final short max;
    private final double avg;
    private final double std;

    public AxisStats(short min, short max, double avg, double std) {
        this.min = min;
        this.max = max;
        this.avg = avg;
        this.std = std;
    }

    public short getMin() { return min; }
    public short getMax() { return max; }
    public double getAvg() { return avg; }
    public double getStd() { return std; }
}
