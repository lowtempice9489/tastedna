package com.tastedna.session.api.dto;

import jakarta.validation.constraints.NotNull;

public record StartSessionRequest(
        @NotNull Mode mode,
        @NotNull ApparelScope apparelScope
) {
}