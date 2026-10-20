package com.tastedna.definition.api.dto;

import java.util.List;
import java.util.UUID;

public record QuestionListResponse(
        UUID sessionId,
        List<QuestionResponse> questions
) {
    public QuestionListResponse {
        questions = List.copyOf(questions);
    }
}
