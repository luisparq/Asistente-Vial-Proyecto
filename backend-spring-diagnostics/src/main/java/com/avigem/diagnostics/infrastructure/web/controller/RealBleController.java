package com.avigem.diagnostics.infrastructure.web.controller;

import com.avigem.diagnostics.application.dto.RealBleBatchRequest;
import com.avigem.diagnostics.application.dto.RealBleBatchResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.application.usecase.RealBleIngestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diagnostics/real/sessions")
public class RealBleController {
    private final RealBleIngestService service;

    public RealBleController(RealBleIngestService service) { this.service = service; }

    @PostMapping("/{clientSessionId}/samples")
    public ResponseEntity<RealBleBatchResponse> ingest(
            @PathVariable String clientSessionId,
            @Valid @RequestBody RealBleBatchRequest request) {
        return ResponseEntity.ok(service.ingest(clientSessionId, request));
    }

    @PostMapping("/{clientSessionId}/complete")
    public ResponseEntity<SessionResponse> complete(@PathVariable String clientSessionId) {
        return ResponseEntity.ok(service.complete(clientSessionId));
    }
}
