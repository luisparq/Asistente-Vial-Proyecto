package com.avigem.diagnostics.infrastructure.web;

import com.avigem.diagnostics.application.dto.AddSamplesRequest;
import com.avigem.diagnostics.application.dto.DeviceResponse;
import com.avigem.diagnostics.application.dto.RegisterDeviceRequest;
import com.avigem.diagnostics.application.dto.SessionDetailResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.application.dto.StartSessionRequest;
import com.avigem.diagnostics.application.port.in.DiagnosticsUseCase;
import com.avigem.diagnostics.config.WebConfig;
import com.avigem.diagnostics.domain.model.DataOrigin;
import com.avigem.diagnostics.domain.model.Device;
import com.avigem.diagnostics.domain.model.DiagnosticSession;
import com.avigem.diagnostics.domain.model.DuplicateResourceException;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;
import com.avigem.diagnostics.domain.model.ResourceNotFoundException;
import com.avigem.diagnostics.domain.model.SessionResult;
import com.avigem.diagnostics.domain.model.SessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas REST de la capa web — sin base de datos, con MockMvc.
 */
@WebMvcTest
@Import(WebConfig.class)
@DisplayName("DiagnosticsController - pruebas REST")
class DiagnosticsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean DiagnosticsUseCase useCase;

    private static final String BASE = "/api/v1/diagnostics";

    // ── POST /devices ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /devices → 201 con deviceCode válido")
    void registerDevice_returns201() throws Exception {
        DeviceResponse resp = DeviceResponse.from(
            new Device(1L, "AVIGEM-001", "Test device", Instant.now()));

        RegisterDeviceRequest req = new RegisterDeviceRequest("AVIGEM-001", "Test device");
        when(useCase.registerDevice(any())).thenReturn(resp);

        mockMvc.perform(post(BASE + "/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deviceCode").value("AVIGEM-001"));
    }

    @Test
    @DisplayName("POST /devices → 400 cuando deviceCode está vacío")
    void registerDevice_emptyCode_returns400() throws Exception {
        RegisterDeviceRequest req = new RegisterDeviceRequest("", null);

        mockMvc.perform(post(BASE + "/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /devices → 409 cuando el código ya existe")
    void registerDevice_duplicate_returns409() throws Exception {
        when(useCase.registerDevice(any()))
            .thenThrow(new DuplicateResourceException("Ya existe un dispositivo con código: AVIGEM-001"));

        RegisterDeviceRequest req = new RegisterDeviceRequest("AVIGEM-001", "Duplicate");

        mockMvc.perform(post(BASE + "/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ── GET /devices ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /devices → 200 con listado paginado")
    void getDevices_returns200() throws Exception {
        DeviceResponse device = DeviceResponse.from(
            new Device(1L, "AVIGEM-001", "Test device", Instant.now()));
        when(useCase.getDevices(any(PageQuery.class)))
            .thenReturn(PageResult.of(List.of(device), 0, 20, 1));

        mockMvc.perform(get(BASE + "/devices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].deviceCode").value("AVIGEM-001"));
    }

    @Test
    @DisplayName("GET /devices/1 → 200 con detalle del dispositivo")
    void getDevice_returns200() throws Exception {
        DeviceResponse device = DeviceResponse.from(
            new Device(1L, "AVIGEM-001", "Test device", Instant.now()));
        when(useCase.getDevice(1L)).thenReturn(device);

        mockMvc.perform(get(BASE + "/devices/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceCode").value("AVIGEM-001"));
    }

    @Test
    @DisplayName("GET /devices/999 → 404 cuando no existe")
    void getDevice_notFound_returns404() throws Exception {
        when(useCase.getDevice(999L))
            .thenThrow(new ResourceNotFoundException("Dispositivo no encontrado: 999"));

        mockMvc.perform(get(BASE + "/devices/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ── POST /devices/{id}/sessions ───────────────────────────────────────────

    @Test
    @DisplayName("POST /devices/1/sessions → 201 con TEST_DATA asignado")
    void startSession_returns201() throws Exception {
        SessionResponse resp = buildSessionResponse(
            SessionStatus.IN_PROGRESS, SessionResult.PENDING);
        when(useCase.startSession(eq(1L), any())).thenReturn(resp);

        StartSessionRequest req = new StartSessionRequest("Prueba inicial");
        mockMvc.perform(post(BASE + "/devices/1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.dataOrigin").value("TEST_DATA"));
    }

    @Test
    @DisplayName("POST /devices/99/sessions → 404 cuando dispositivo no existe")
    void startSession_deviceNotFound_returns404() throws Exception {
        when(useCase.startSession(eq(99L), any()))
            .thenThrow(new ResourceNotFoundException("Dispositivo no encontrado: 99"));

        mockMvc.perform(post(BASE + "/devices/99/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ── POST /sessions/{id}/samples ───────────────────────────────────────────

    @Test
    @DisplayName("POST /sessions/10/samples → 200 con lote válido")
    void addSamples_returns200() throws Exception {
        SessionResponse resp = buildSessionResponse(
            SessionStatus.IN_PROGRESS, SessionResult.PENDING);
        when(useCase.addSamples(eq(10L), any())).thenReturn(resp);

        AddSamplesRequest req = new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(100, 200, 300, -100, -200, -300, Instant.now())
        ));

        mockMvc.perform(post(BASE + "/sessions/10/samples")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /sessions/10/samples → 400 cuando lista está vacía")
    void addSamples_emptyList_returns400() throws Exception {
        AddSamplesRequest req = new AddSamplesRequest(List.of());

        mockMvc.perform(post(BASE + "/sessions/10/samples")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /sessions/10/samples → 400 cuando hay una muestra nula")
    void addSamples_nullElement_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/sessions/10/samples")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"samples\":[null]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /sessions/10/samples → 400 con JSON inválido")
    void addSamples_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/sessions/10/samples")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"samples\":["))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    // ── POST /sessions/{id}/complete ──────────────────────────────────────────

    @Test
    @DisplayName("POST /sessions/10/complete → 200 con COMPLETED/OBSERVED")
    void completeSession_returns200() throws Exception {
        SessionResponse resp = buildSessionResponse(
            SessionStatus.COMPLETED, SessionResult.OBSERVED);
        when(useCase.completeSession(10L)).thenReturn(resp);

        mockMvc.perform(post(BASE + "/sessions/10/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.result").value("OBSERVED"))
            .andExpect(jsonPath("$.resultNote").isNotEmpty());
    }

    @Test
    @DisplayName("POST /sessions/10/complete sin muestras → 200 con COMPLETED/NOT_EVALUATED")
    void completeSession_withoutSamples_returnsNotEvaluated() throws Exception {
        SessionResponse resp = buildSessionResponse(
            SessionStatus.COMPLETED, SessionResult.NOT_EVALUATED);
        when(useCase.completeSession(10L)).thenReturn(resp);

        mockMvc.perform(post(BASE + "/sessions/10/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.result").value("NOT_EVALUATED"))
            .andExpect(jsonPath("$.resultNote")
                .value("Sin datos suficientes para evaluar la sesión"));
    }

    // ── POST /sessions/{id}/cancel ────────────────────────────────────────────

    @Test
    @DisplayName("POST /sessions/10/cancel → 200 con CANCELLED/NOT_EVALUATED")
    void cancelSession_returns200() throws Exception {
        SessionResponse resp = buildSessionResponse(
            SessionStatus.CANCELLED, SessionResult.NOT_EVALUATED);
        when(useCase.cancelSession(eq(10L), anyString())).thenReturn(resp);

        mockMvc.perform(post(BASE + "/sessions/10/cancel")
                .param("reason", "Interrupción de prueba"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.result").value("NOT_EVALUATED"));
    }

    // ── GET /sessions/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /sessions/10 → 200 con detalle de sesión")
    void getSessionDetail_returns200() throws Exception {
        DiagnosticSession session = new DiagnosticSession(
            10L, 1L, SessionStatus.COMPLETED, SessionResult.OBSERVED,
            null, Instant.now(), Instant.now()
        );
        session.setDataOrigin(DataOrigin.TEST_DATA);
        SessionDetailResponse detail = SessionDetailResponse.from(session, null);
        when(useCase.getSessionDetail(10L)).thenReturn(detail);

        mockMvc.perform(get(BASE + "/sessions/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("GET /sessions/999 → 404 cuando no existe")
    void getSessionDetail_notFound_returns404() throws Exception {
        when(useCase.getSessionDetail(999L))
            .thenThrow(new ResourceNotFoundException("Sesión no encontrada: 999"));

        mockMvc.perform(get(BASE + "/sessions/999"))
            .andExpect(status().isNotFound());
    }

    // ── GET /devices/{id}/sessions ────────────────────────────────────────────

    @Test
    @DisplayName("GET /devices/1/sessions → 200 con historial paginado")
    void getSessionHistory_returns200() throws Exception {
        SessionResponse sr = buildSessionResponse(
            SessionStatus.COMPLETED, SessionResult.OBSERVED);
        when(useCase.getSessionHistory(eq(1L), any(PageQuery.class)))
            .thenReturn(PageResult.of(List.of(sr), 0, 20, 1));

        mockMvc.perform(get(BASE + "/devices/1/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /devices/99/sessions → 404 cuando dispositivo no existe")
    void getSessionHistory_deviceNotFound_returns404() throws Exception {
        when(useCase.getSessionHistory(eq(99L), any(PageQuery.class)))
            .thenThrow(new ResourceNotFoundException("Dispositivo no encontrado: 99"));

        mockMvc.perform(get(BASE + "/devices/99/sessions"))
            .andExpect(status().isNotFound());
    }

    // ── 400/404 de infraestructura ────────────────────────────────────────────

    @Test
    @DisplayName("GET /sessions/abc → 400 por tipo inválido")
    void invalidPathVariable_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/sessions/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("Ruta inexistente → 404")
    void unknownRoute_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/unknown"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ── 422 Business Rule ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST samples → 422 cuando sesión no está EN PROGRESO")
    void addSamples_sessionNotInProgress_returns422() throws Exception {
        when(useCase.addSamples(eq(10L), any()))
            .thenThrow(new com.avigem.diagnostics.domain.model.BusinessRuleException(
                "La sesión 10 no está EN PROGRESO"));

        AddSamplesRequest req = new AddSamplesRequest(List.of(
            new AddSamplesRequest.SampleItem(1, 2, 3, 4, 5, 6, null)
        ));

        mockMvc.perform(post(BASE + "/sessions/10/samples")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private SessionResponse buildSessionResponse(SessionStatus status, SessionResult result) {
        DiagnosticSession s = new DiagnosticSession(
            10L, 1L, status, result, null, Instant.now(), null
        );
        s.setDataOrigin(DataOrigin.TEST_DATA);
        return SessionResponse.from(s);
    }
}
