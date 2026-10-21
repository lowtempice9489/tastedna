package com.tastedna.session.application;

import tools.jackson.databind.JsonNode;

public final class SingleChoiceAnswerPayloadExtractor {

    private SingleChoiceAnswerPayloadExtractor() {
    }

    public static String extract(JsonNode answerPayload) {

        if (answerPayload == null || !answerPayload.isObject()) {
            throw new IllegalArgumentException(
                    "INVALID_ANSWER_PAYLOAD"
            );
        }

        if (answerPayload.size() != 1) {
            throw new IllegalArgumentException(
                    "INVALID_ANSWER_PAYLOAD"
            );
        }

        JsonNode selectedOptionId =
                answerPayload.get("selectedOptionId");

        if (selectedOptionId == null
                || !selectedOptionId.isString()
                || selectedOptionId.stringValue().isBlank()) {
            throw new IllegalArgumentException(
                    "INVALID_ANSWER_PAYLOAD"
            );
        }

        return selectedOptionId.stringValue();
    }
}
