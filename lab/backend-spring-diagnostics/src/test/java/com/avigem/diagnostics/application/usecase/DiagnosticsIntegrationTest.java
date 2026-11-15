package com.avigem.diagnostics.application.usecase;

import com.avigem.diagnostics.application.dto.AddSamplesRequest;
import com.avigem.diagnostics.application.dto.DeviceResponse;
import com.avigem.diagnostics.application.dto.RegisterDeviceRequest;
import com.avigem.diagnostics.application.dto.SessionDetailResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.application.dto.StartSessionRequest;
import com.avigem.diagnostics.application.port.in.DiagnosticsUseCase;
import com.avigem.diagnostics.domain.model.DataOrigin;
import com.avigem.diagnostics.domain.model.DuplicateResourceException;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;
import com.avigem.diagnostics.domain.model.SessionResult;
import com.avigem.diagnostics.domain.model.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Prueba de integración: flujo completo de sesión con H2 + Flyway.
 * Todos los datos son TEST_DATA.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("DiagnosticsService - flujo completo integración")
class DiagnosticsIntegrationTest {

    @Autowired
    private DiagnosticsUseCase useCase;

    @Test
    @DisplayName("Flujo completo: registrar → iniciar → samples → completar → detalle")
    void fullSessionFlow() {
        // 1. Registrar dispositivo
        RegisterDeviceRequest devReq = new RegisterDeviceRequest("AVIGEM-INT-001", "Integration test");
        DeviceResponse device = useCase.registerDevice(devReq);
        assertThat(device.getId()).isNotNull();
        assertThat(device.getDeviceCode()).isEqualTo("AVIGEM-INT-001");

        // 2. Iniciar sesión: el backend asigna TEST_DATA
        SessionResponse session = useCase.startSession(device.getId(), new StartSessionRequest("Flujo test"));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getResult()).isEqualTo(SessionResult.PENDING);
        assertThat(session.getDataOrigin()).isEqualTo(DataOrigin.TEST_DATA);

        // 3. Agregar muestras TEST_DATA
        AddSamplesRequest samplesReq = new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(1000, 2000, -500,  300, -100,  200, null),
            new AddSamplesRequest.SampleItem(1200, 1800, -400,  400, -200,  100, null),
            new AddSamplesRequest.SampleItem( 800, 2200, -600,  200,    0,  300, null)
        ));
        useCase.addSamples(session.getId(), samplesReq);

        // 4. Completar sesión: TEST_DATA nunca produce PASSED
        SessionResponse completed = useCase.completeSession(session.getId());
        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getResult()).isEqualTo(SessionResult.OBSERVED);
        assertThat(completed.getResult()).isNotEqualTo(SessionResult.PASSED);
        assertThat(completed.getResultNote()).contains("flujo de software");

        // 5. Consultar detalle con resumen
        SessionDetailResponse detail = useCase.getSessionDetail(session.getId());
        assertThat(detail.getSummary()).isNotNull();
        assertThat(detail.getSummary().getSampleCount()).isEqualTo(3);
        assertThat(detail.getDataOrigin()).isEqualTo(DataOrigin.TEST_DATA);

        // Verificar estadísticas eje ax: 1000, 1200, 800 → avg=1000, min=800, max=1200
        SessionDetailResponse.AxisStats ax = detail.getSummary().getAx();
        assertThat(ax.getMin()).isEqualTo((short) 800);
        assertThat(ax.getMax()).isEqualTo((short) 1200);
        assertThat(ax.getAvg()).isCloseTo(1000.0, within(1e-6));
        // std = sqrt(((1000-800)² + (1000-1200)² + (1000-1000)²)/3) = sqrt(80000/3)
        double expectedStd = Math.sqrt(80000.0 / 3.0);
        assertThat(ax.getStd()).isCloseTo(expectedStd, within(1e-6));
    }

    @Test
    @DisplayName("Código de dispositivo duplicado lanza DuplicateResourceException")
    void duplicateDeviceCode_throws() {
        useCase.registerDevice(new RegisterDeviceRequest("AVIGEM-DUP-001", "First"));

        assertThatThrownBy(() ->
            useCase.registerDevice(new RegisterDeviceRequest("AVIGEM-DUP-001", "Second"))
        ).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Duplicado con espacios se detecta después del trim")
    void duplicateDeviceCodeWithSpaces_throws() {
        useCase.registerDevice(new RegisterDeviceRequest("AVIGEM-SPACE-001", "First"));

        assertThatThrownBy(() ->
            useCase.registerDevice(new RegisterDeviceRequest("  AVIGEM-SPACE-001  ", "Second"))
        ).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Cancelar sesión: CANCELLED + NOT_EVALUATED y conserva notas")
    void cancelFlow() {
        DeviceResponse device = useCase.registerDevice(
            new RegisterDeviceRequest("AVIGEM-CAN-001", "Cancel test"));
        SessionResponse session = useCase.startSession(
            device.getId(), new StartSessionRequest("Notas originales"));

        SessionResponse cancelled = useCase.cancelSession(session.getId(), "Test cancelado");

        assertThat(cancelled.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(cancelled.getResult()).isEqualTo(SessionResult.NOT_EVALUATED);
        assertThat(cancelled.getNotes()).isEqualTo("Notas originales");
        assertThat(cancelled.getCancellationReason()).isEqualTo("Test cancelado");
    }

    @Test
    @DisplayName("Sesión completada sin muestras resulta en COMPLETED/NOT_EVALUATED sin resumen")
    void completeWithoutSamples_notEvaluated() {
        DeviceResponse device = useCase.registerDevice(
            new RegisterDeviceRequest("AVIGEM-OBS-001", "Observed test"));
        SessionResponse session = useCase.startSession(device.getId(), null);

        SessionResponse completed = useCase.completeSession(session.getId());
        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getResult()).isEqualTo(SessionResult.NOT_EVALUATED);
        assertThat(completed.getResultNote())
            .isEqualTo("Sin datos suficientes para evaluar la sesión");

        SessionDetailResponse detail = useCase.getSessionDetail(session.getId());
        assertThat(detail.getSummary()).isNull();
    }

    @Test
    @DisplayName("Los límites RAW int16 se persisten y se resumen sin truncamiento")
    void rawInt16BoundariesPersisted() {
        DeviceResponse device = useCase.registerDevice(
            new RegisterDeviceRequest("AVIGEM-RAW-001", "RAW boundary test"));
        SessionResponse session = useCase.startSession(device.getId(), null);

        useCase.addSamples(session.getId(), new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(
                Short.MIN_VALUE, Short.MAX_VALUE, 0, 0, 0, 0, null)
        )));
        useCase.completeSession(session.getId());

        SessionDetailResponse detail = useCase.getSessionDetail(session.getId());
        assertThat(detail.getSummary().getAx().getMin()).isEqualTo(Short.MIN_VALUE);
        assertThat(detail.getSummary().getAx().getMax()).isEqualTo(Short.MIN_VALUE);
        assertThat(detail.getSummary().getAy().getMin()).isEqualTo(Short.MAX_VALUE);
        assertThat(detail.getSummary().getAy().getMax()).isEqualTo(Short.MAX_VALUE);
    }

    @Test
    @DisplayName("Listar y consultar dispositivos")
    void deviceListAndGet() {
        DeviceResponse first = useCase.registerDevice(
            new RegisterDeviceRequest("AVIGEM-LIST-001", "First"));
        DeviceResponse second = useCase.registerDevice(
            new RegisterDeviceRequest("AVIGEM-LIST-002", "Second"));

        DeviceResponse byId = useCase.getDevice(first.getId());
        assertThat(byId.getDeviceCode()).isEqualTo("AVIGEM-LIST-001");

        PageResult<DeviceResponse> page = useCase.getDevices(
            new PageQuery(0, 10, "registeredAt", false));
        assertThat(page.content())
            .extracting(DeviceResponse::getDeviceCode)
            .contains("AVIGEM-LIST-001", "AVIGEM-LIST-002");
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(second.getId()).isNotNull();
    }

    @Test
    @DisplayName("Historial paginado de sesiones")
    void sessionHistoryPaged() {
        DeviceResponse device = useCase.registerDevice(
            new RegisterDeviceRequest("AVIGEM-HIST-001", "History test"));
        useCase.startSession(device.getId(), new StartSessionRequest("Sesión 1"));
        useCase.startSession(device.getId(), new StartSessionRequest("Sesión 2"));

        PageResult<SessionResponse> page = useCase.getSessionHistory(
            device.getId(), new PageQuery(0, 10, "startedAt", false));

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(2);
    }
}
