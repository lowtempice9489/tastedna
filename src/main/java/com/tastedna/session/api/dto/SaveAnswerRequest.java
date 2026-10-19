package com.tastedna.session.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record SaveAnswerRequest(

        @NotBlank
        String questionId,

        @NotNull
        @Min(0)
        Long expectedRevision,

        @NotNull
        JsonNode answerPayload

) {
}
