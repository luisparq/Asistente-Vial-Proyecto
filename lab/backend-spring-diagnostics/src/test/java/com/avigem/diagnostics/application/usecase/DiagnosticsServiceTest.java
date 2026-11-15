package com.avigem.diagnostics.application.usecase;

import com.avigem.diagnostics.application.dto.AddSamplesRequest;
import com.avigem.diagnostics.application.dto.DeviceResponse;
import com.avigem.diagnostics.application.dto.RegisterDeviceRequest;
import com.avigem.diagnostics.application.dto.SessionDetailResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.application.dto.StartSessionRequest;
import com.avigem.diagnostics.domain.model.BusinessRuleException;
import com.avigem.diagnostics.domain.model.DataOrigin;
import com.avigem.diagnostics.domain.model.Device;
import com.avigem.diagnostics.domain.model.DiagnosticSession;
import com.avigem.diagnostics.domain.model.DuplicateResourceException;
import com.avigem.diagnostics.domain.model.ImuSample;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;
import com.avigem.diagnostics.domain.model.ResourceNotFoundException;
import com.avigem.diagnostics.domain.model.SessionResult;
import com.avigem.diagnostics.domain.model.SessionStatus;
import com.avigem.diagnostics.domain.port.out.DeviceRepository;
import com.avigem.diagnostics.domain.port.out.ImuSampleRepository;
import com.avigem.diagnostics.domain.port.out.SessionRepository;
import com.avigem.diagnostics.domain.port.out.SummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de DiagnosticsService con mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiagnosticsService - casos de uso")
class DiagnosticsServiceTest {

    @Mock private DeviceRepository deviceRepo;
    @Mock private SessionRepository sessionRepo;
    @Mock private ImuSampleRepository sampleRepo;
    @Mock private SummaryRepository summaryRepo;

    @InjectMocks
    private DiagnosticsService service;

    private Device mockDevice;
    private DiagnosticSession mockSession;

    @BeforeEach
    void setUp() {
        mockDevice = new Device(1L, "AVIGEM-TEST-001", "Dispositivo de prueba", Instant.now());
        mockSession = new DiagnosticSession(
            10L, 1L, SessionStatus.IN_PROGRESS, SessionResult.PENDING,
            "Notas originales", Instant.now(), null
        );
        mockSession.setDataOrigin(DataOrigin.TEST_DATA);
    }

    // ── registerDevice ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Registrar dispositivo nuevo normaliza deviceCode con trim")
    void registerDevice_success() {
        when(deviceRepo.existsByDeviceCode("AVIGEM-TEST-001")).thenReturn(false);
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterDeviceRequest req = new RegisterDeviceRequest("  AVIGEM-TEST-001  ", "Test");
        DeviceResponse resp = service.registerDevice(req);

        assertThat(resp.getDeviceCode()).isEqualTo("AVIGEM-TEST-001");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepo).save(captor.capture());
        assertThat(captor.getValue().getDeviceCode()).isEqualTo("AVIGEM-TEST-001");
    }

    @Test
    @DisplayName("Duplicado con espacios se detecta después del trim")
    void registerDevice_duplicateWithSpaces_throwsConflict() {
        when(deviceRepo.existsByDeviceCode("AVIGEM-TEST-001")).thenReturn(true);

        RegisterDeviceRequest req = new RegisterDeviceRequest("  AVIGEM-TEST-001  ", "Test");

        assertThatThrownBy(() -> service.registerDevice(req))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("AVIGEM-TEST-001");

        verify(deviceRepo, never()).save(any());
    }

    @Test
    @DisplayName("deviceCode vacío después del trim se rechaza")
    void registerDevice_blankAfterTrim_throws() {
        RegisterDeviceRequest req = new RegisterDeviceRequest("   ", "Test");

        assertThatThrownBy(() -> service.registerDevice(req))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("vacío");

        verify(deviceRepo, never()).existsByDeviceCode(anyString());
    }

    // ── startSession ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Iniciar sesión asigna TEST_DATA en el backend")
    void startSession_success() {
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(mockDevice));
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse resp = service.startSession(1L, new StartSessionRequest("Test sesión"));

        assertThat(resp.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(resp.getResult()).isEqualTo(SessionResult.PENDING);
        assertThat(resp.getDataOrigin()).isEqualTo(DataOrigin.TEST_DATA);
    }

    @Test
    @DisplayName("Iniciar sesión en dispositivo inexistente lanza ResourceNotFoundException")
    void startSession_deviceNotFound_throws() {
        when(deviceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startSession(99L, null))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("99");
    }

    // ── addSamples ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Agregar muestras a sesión en progreso")
    void addSamples_success() {
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));
        when(sampleRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        AddSamplesRequest req = new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(100, 200, -50, 300, -100, 0, null),
            new AddSamplesRequest.SampleItem(-200, 100, 50, -300, 200, 10, null)
        ));

        SessionResponse resp = service.addSamples(10L, req);

        assertThat(resp.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(sampleRepo).saveAll(argThat(list -> list.size() == 2));
    }

    @Test
    @DisplayName("Agregar muestras a sesión completada lanza BusinessRuleException")
    void addSamples_toCompletedSession_throws() {
        DiagnosticSession completed = new DiagnosticSession(
            10L, 1L, SessionStatus.COMPLETED, SessionResult.OBSERVED, null, Instant.now(), Instant.now()
        );
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(completed));

        AddSamplesRequest req = new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(1, 2, 3, 4, 5, 6, null)
        ));

        assertThatThrownBy(() -> service.addSamples(10L, req))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("Valor RAW fuera de rango int16 lanza BusinessRuleException")
    void addSamples_outOfRangeRaw_throws() {
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));

        AddSamplesRequest req = new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(99999, 0, 0, 0, 0, 0, null)
        ));

        assertThatThrownBy(() -> service.addSamples(10L, req))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("99999");
    }

    @Test
    @DisplayName("Elemento nulo dentro del lote se rechaza")
    void addSamples_nullElement_throws() {
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));

        AddSamplesRequest req = new AddSamplesRequest(Collections.singletonList(null));

        assertThatThrownBy(() -> service.addSamples(10L, req))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("nula");

        verify(sampleRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("Los límites RAW int16 se conservan sin truncamiento")
    void addSamples_rawInt16Boundaries() {
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));
        when(sampleRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        AddSamplesRequest req = new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(
                Short.MIN_VALUE, Short.MAX_VALUE, 0, 0, 0, 0, null)
        ));

        service.addSamples(10L, req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImuSample>> captor = ArgumentCaptor.forClass(List.class);
        verify(sampleRepo).saveAll(captor.capture());

        ImuSample stored = captor.getValue().get(0);
        assertThat(stored.getAxRaw()).isEqualTo(Short.MIN_VALUE);
        assertThat(stored.getAyRaw()).isEqualTo(Short.MAX_VALUE);
    }

    // ── completeSession ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Completar sesión TEST_DATA con muestras queda OBSERVED, nunca PASSED")
    void completeSession_withSamples_observed() {
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));

        List<ImuSample> samples = List.of(
            new ImuSample(1L, 10L, (short) 100, (short) 200, (short) 300,
                          (short) -100, (short) -200, (short) -300, Instant.now()),
            new ImuSample(2L, 10L, (short) 150, (short) 250, (short) 350,
                          (short) -150, (short) -250, (short) -350, Instant.now())
        );
        when(sampleRepo.findBySessionId(10L)).thenReturn(samples);
        when(summaryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse resp = service.completeSession(10L);

        assertThat(resp.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(resp.getResult()).isEqualTo(SessionResult.OBSERVED);
        assertThat(resp.getResult()).isNotEqualTo(SessionResult.PASSED);
        assertThat(resp.getDataOrigin()).isEqualTo(DataOrigin.TEST_DATA);
        assertThat(resp.getResultNote()).contains("flujo de software");
        verify(summaryRepo).save(any());
    }

    @Test
    @DisplayName("Completar sesión sin muestras queda COMPLETED/NOT_EVALUATED y sin resumen")
    void completeSession_noSamples_notEvaluated() {
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));
        when(sampleRepo.findBySessionId(10L)).thenReturn(List.of());
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse resp = service.completeSession(10L);

        assertThat(resp.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(resp.getResult()).isEqualTo(SessionResult.NOT_EVALUATED);
        assertThat(resp.getResultNote())
            .isEqualTo("Sin datos suficientes para evaluar la sesión");
        verify(summaryRepo, never()).save(any());
    }

    @Test
    @DisplayName("addSamples rechaza sesiones REAL_MQTT/REAL_BLE aunque estén en progreso")
    void addSamples_realOrigin_throws() {
        mockSession.setDataOrigin(DataOrigin.REAL_MQTT);
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));

        AddSamplesRequest req = new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(1, 2, 3, 4, 5, 6, null)
        ));

        assertThatThrownBy(() -> service.addSamples(10L, req))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("TEST_DATA");

        verify(sampleRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("completeSession rechaza sesiones reales y no sobrescribe su origen")
    void completeSession_realOrigin_throws() {
        mockSession.setDataOrigin(DataOrigin.REAL_BLE);
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));

        assertThatThrownBy(() -> service.completeSession(10L))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("TEST_DATA");

        verify(sessionRepo, never()).save(any());
        assertThat(mockSession.getDataOrigin()).isEqualTo(DataOrigin.REAL_BLE);
    }

    // ── cancelSession ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cancelar sesión: CANCELLED + NOT_EVALUATED y conserva notas")
    void cancelSession_success() {
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse resp = service.cancelSession(10L, "Cancelado por usuario");

        assertThat(resp.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(resp.getResult()).isEqualTo(SessionResult.NOT_EVALUATED);
        assertThat(resp.getNotes()).isEqualTo("Notas originales");
        assertThat(resp.getCancellationReason()).isEqualTo("Cancelado por usuario");
    }

    @Test
    @DisplayName("Motivo de cancelación mayor a 500 caracteres se rechaza")
    void cancelSession_reasonTooLong_throws() {
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(mockSession));

        String reason = "x".repeat(501);

        assertThatThrownBy(() -> service.cancelSession(10L, reason))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("500");

        verify(sessionRepo, never()).save(any());
    }

    // ── getSessionDetail ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Detalle de sesión existente retorna datos correctos")
    void getSessionDetail_success() {
        DiagnosticSession completed = new DiagnosticSession(
            10L, 1L, SessionStatus.COMPLETED, SessionResult.OBSERVED,
            "Notas de prueba", Instant.now(), Instant.now()
        );
        completed.setDataOrigin(DataOrigin.TEST_DATA);
        when(sessionRepo.findById(10L)).thenReturn(Optional.of(completed));
        when(summaryRepo.findBySessionId(10L)).thenReturn(Optional.empty());

        SessionDetailResponse detail = service.getSessionDetail(10L);

        assertThat(detail.getId()).isEqualTo(10L);
        assertThat(detail.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(detail.getSummary()).isNull();
    }

    @Test
    @DisplayName("Sesión inexistente lanza ResourceNotFoundException")
    void getSessionDetail_notFound_throws() {
        when(sessionRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSessionDetail(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("999");
    }

    // ── Dispositivos: consultas ───────────────────────────────────────────────

    @Test
    @DisplayName("Obtener dispositivo existente")
    void getDevice_success() {
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(mockDevice));

        DeviceResponse resp = service.getDevice(1L);

        assertThat(resp.getDeviceCode()).isEqualTo("AVIGEM-TEST-001");
    }

    @Test
    @DisplayName("Obtener dispositivo inexistente lanza ResourceNotFoundException")
    void getDevice_notFound_throws() {
        when(deviceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDevice(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Listar dispositivos paginados")
    void getDevices_success() {
        when(deviceRepo.findAll(any(PageQuery.class)))
            .thenReturn(PageResult.of(List.of(mockDevice), 0, 20, 1));

        PageResult<DeviceResponse> result = service.getDevices(
            new PageQuery(0, 20, "registeredAt", false));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getDeviceCode()).isEqualTo("AVIGEM-TEST-001");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    // ── Historial ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Historial paginado de un dispositivo existente")
    void getSessionHistory_success() {
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(mockDevice));
        when(sessionRepo.findByDeviceId(eq(1L), any(PageQuery.class)))
            .thenReturn(PageResult.of(List.of(mockSession), 0, 20, 1));

        PageResult<SessionResponse> result = service.getSessionHistory(
            1L, new PageQuery(0, 20, "startedAt", false));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Historial de dispositivo inexistente lanza ResourceNotFoundException")
    void getSessionHistory_deviceNotFound_throws() {
        when(deviceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSessionHistory(
            99L, new PageQuery(0, 20, "startedAt", false)))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
