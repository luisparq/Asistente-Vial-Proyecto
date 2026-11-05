package com.avigem.diagnostics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AVIGEM One Diagnostics - MVP
 *
 * Módulo independiente de diagnóstico IMU con arquitectura hexagonal.
 * NOTA: En esta etapa solo se aceptan TEST_DATA.
 *       No se afirma que los datos sean mediciones físicas reales.
 */
@SpringBootApplication
public class DiagnosticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiagnosticsApplication.class, args);
    }
}
