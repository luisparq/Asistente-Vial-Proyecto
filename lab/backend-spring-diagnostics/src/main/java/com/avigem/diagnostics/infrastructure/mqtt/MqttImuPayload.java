package com.avigem.diagnostics.infrastructure.mqtt;

public record MqttImuPayload(
    String sessionId, String deviceCode, Long sequence,
    Integer axRaw, Integer ayRaw, Integer azRaw,
    Integer gxRaw, Integer gyRaw, Integer gzRaw
) {}
