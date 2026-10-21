package com.tastedna.session.application.multi;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MultiChoiceAnswerPayloadExtractor {

    private MultiChoiceAnswerPayloadExtractor() {
    }

    public static List<String> extract(JsonNode answerPayload) {

        if (answerPayload == null || !answerPayload.isObject()
                || answerPayload.size() != 1) {
            throw new IllegalArgumentException(
                    "INVALID_ANSWER_PAYLOAD"
            );
        }

        JsonNode selectedOptionIds =
                answerPayload.get("selectedOptionIds");

        if (selectedOptionIds == null
                || !selectedOptionIds.isArray()
                || selectedOptionIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "INVALID_ANSWER_PAYLOAD"
            );
        }

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (JsonNode selectedOptionId : selectedOptionIds) {

            if (!selectedOptionId.isString()) {
                throw new IllegalArgumentException(
                        "INVALID_ANSWER_PAYLOAD"
                );
            }

            String optionId = selectedOptionId.stringValue();

            if (optionId.isBlank() || !seen.add(optionId)) {
                throw new IllegalArgumentException(
                        "INVALID_ANSWER_PAYLOAD"
                );
            }

            result.add(optionId);
        }

        return List.copyOf(result);
    }
}
