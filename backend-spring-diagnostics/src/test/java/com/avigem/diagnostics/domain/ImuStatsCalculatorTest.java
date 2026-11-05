package com.avigem.diagnostics.domain;

import com.avigem.diagnostics.domain.model.AxisStats;
import com.avigem.diagnostics.domain.model.ImuSample;
import com.avigem.diagnostics.domain.model.ImuStatsCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias del calculador estadístico — dominio puro, sin Spring.
 * Todos los valores son TEST_DATA.
 */
@DisplayName("ImuStatsCalculator - cálculos estadísticos RAW int16")
class ImuStatsCalculatorTest {

    private ImuSample sample(short ax, short ay, short az,
                              short gx, short gy, short gz) {
        return new ImuSample(null, 1L, ax, ay, az, gx, gy, gz, Instant.now());
    }

    @Test
    @DisplayName("Muestra única: min == max == avg, std == 0")
    void singleSample() {
        List<ImuSample> samples = List.of(sample((short) 100, (short) 200, (short) -50,
                                                  (short) 300, (short) -100, (short) 0));

        AxisStats ax = ImuStatsCalculator.computeAxis(samples, s -> s.getAxRaw());

        assertEquals((short) 100, ax.getMin());
        assertEquals((short) 100, ax.getMax());
        assertEquals(100.0, ax.getAvg(), 1e-9);
        assertEquals(0.0, ax.getStd(), 1e-9);
    }

    @Test
    @DisplayName("Tres muestras: min, max, avg y std correctos")
    void threeSamples() {
        // ax: 10, 20, 30 → avg=20, var=((10-20)²+(20-20)²+(30-20)²)/3 = 200/3, std=√(200/3)
        List<ImuSample> samples = List.of(
            sample((short) 10, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0),
            sample((short) 20, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0),
            sample((short) 30, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0)
        );

        AxisStats ax = ImuStatsCalculator.computeAxis(samples, s -> s.getAxRaw());

        assertEquals((short) 10, ax.getMin());
        assertEquals((short) 30, ax.getMax());
        assertEquals(20.0, ax.getAvg(), 1e-9);
        double expectedStd = Math.sqrt(200.0 / 3.0);
        assertEquals(expectedStd, ax.getStd(), 1e-9);
    }

    @Test
    @DisplayName("Valores negativos y mixtos en giroscopio")
    void negativeAndMixedValues() {
        // gz: -100, 0, 100 → avg=0, std=√(20000/3)
        List<ImuSample> samples = List.of(
            sample((short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) -100),
            sample((short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0),
            sample((short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 100)
        );

        AxisStats gz = ImuStatsCalculator.computeAxis(samples, s -> s.getGzRaw());

        assertEquals((short) -100, gz.getMin());
        assertEquals((short) 100, gz.getMax());
        assertEquals(0.0, gz.getAvg(), 1e-9);
        double expectedStd = Math.sqrt(20000.0 / 3.0);
        assertEquals(expectedStd, gz.getStd(), 1e-9);
    }

    @Test
    @DisplayName("Valores en límite int16: Short.MIN_VALUE y Short.MAX_VALUE")
    void int16Boundaries() {
        List<ImuSample> samples = List.of(
            sample(Short.MIN_VALUE, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0),
            sample(Short.MAX_VALUE, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0)
        );

        AxisStats ax = ImuStatsCalculator.computeAxis(samples, s -> s.getAxRaw());

        assertEquals(Short.MIN_VALUE, ax.getMin());
        assertEquals(Short.MAX_VALUE, ax.getMax());
        // avg = (−32768 + 32767) / 2 = -0.5
        assertEquals(-0.5, ax.getAvg(), 1e-6);
    }

    @Test
    @DisplayName("Lista vacía lanza IllegalArgumentException")
    void emptyListThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ImuStatsCalculator.computeAxis(List.of(), s -> s.getAxRaw()));
    }

    @Test
    @DisplayName("Lista null lanza IllegalArgumentException")
    void nullListThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ImuStatsCalculator.computeAxis(null, s -> s.getAxRaw()));
    }

    @Test
    @DisplayName("Desviación estándar poblacional (dividir por N, no N-1)")
    void populationStdNotSample() {
        // Para 2 valores: 0 y 10 → std_pop = 5, std_sample = 7.07...
        List<ImuSample> samples = List.of(
            sample((short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0),
            sample((short) 10, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0)
        );
        AxisStats ax = ImuStatsCalculator.computeAxis(samples, s -> s.getAxRaw());
        // std poblacional = 5.0
        assertEquals(5.0, ax.getStd(), 1e-9);
    }
}
