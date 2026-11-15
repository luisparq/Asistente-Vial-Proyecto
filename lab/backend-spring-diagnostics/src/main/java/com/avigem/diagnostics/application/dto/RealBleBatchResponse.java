package com.avigem.diagnostics.application.dto;

public record RealBleBatchResponse(
    Long sessionId,
    String clientSessionId,
    int accepted,
    int duplicates,
    long storedSamples
) {}
