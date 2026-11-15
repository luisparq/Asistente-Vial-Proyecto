package com.avigem.diagnostics.application.dto;

import com.avigem.diagnostics.domain.model.ImuSample;
import java.time.Instant;

public record ImuSampleResponse(
    Long sequence,
    Instant sampledAt,
    short axRaw, short ayRaw, short azRaw,
    short gxRaw, short gyRaw, short gzRaw
) {
    public static ImuSampleResponse from(ImuSample sample) {
        return new ImuSampleResponse(sample.getSequenceNumber(), sample.getSampledAt(),
            sample.getAxRaw(), sample.getAyRaw(), sample.getAzRaw(),
            sample.getGxRaw(), sample.getGyRaw(), sample.getGzRaw());
    }
}
