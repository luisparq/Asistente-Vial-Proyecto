package com.avigem.diagnostics.application.dto;

import jakarta.validation.constraints.Size;

/**
 * Request para iniciar una sesión de diagnóstico.
 */
public class StartSessionRequest {

    @Size(max = 500, message = "notes no puede superar 500 caracteres")
    private String notes;

    public StartSessionRequest() {}

    public StartSessionRequest(String notes) {
        this.notes = notes;
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
