package com.tastedna.session.api.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
