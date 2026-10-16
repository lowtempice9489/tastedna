
package com.tastedna.session;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class SessionDtos {

    private SessionDtos() {}

    public enum Mode {
        SIMPLE, DEEP
    }

    public enum ApparelScope {
        mens, womens
    }

    public record StartRequest(
            @NotNull Mode mode,
            @NotNull ApparelScope apparelScope
    ) {}

    public record SessionResponse(
            UUID sessionId,
            Mode mode,
            ApparelScope apparelScope,
            String status,
            long revision,
            UUID definitionId,
            Instant leaseUntil
    ) {}

    public record ErrorResponse(
            String code,
            String message
    ) {}
}
