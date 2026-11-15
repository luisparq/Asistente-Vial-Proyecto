package com.avigem.diagnostics.infrastructure.web.controller;

import com.avigem.diagnostics.application.dto.ImuSampleResponse;
import com.avigem.diagnostics.application.usecase.RealBleIngestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Consulta local para la interfaz web; Nginx no debe exponer esta ruta. */
@RestController
@RequestMapping("/api/v1/diagnostics/sessions")
public class ImuSampleQueryController {
    private final RealBleIngestService service;

    public ImuSampleQueryController(RealBleIngestService service) { this.service = service; }

    @GetMapping("/{sessionId}/samples")
    public ResponseEntity<List<ImuSampleResponse>> recent(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "-1") long afterSequence,
            @RequestParam(defaultValue = "500") int limit) {
        return ResponseEntity.ok(service.recent(sessionId, afterSequence, limit));
    }
}
