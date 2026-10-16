package com.tastedna.session.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        Mode mode,
        ApparelScope apparelScope,
        String status,
        long revision,
        UUID definitionId,
        Instant leaseUntil
) {
}