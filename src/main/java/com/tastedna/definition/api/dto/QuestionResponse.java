package com.tastedna.definition.api.dto;

import tools.jackson.databind.JsonNode;

public record QuestionResponse(
        String questionId,
        short displayOrder,
        boolean required,
        JsonNode questionPayload
) {
}
