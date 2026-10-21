package com.tastedna.session.application.comparison;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ComparisonAnswerPayloadExtractor {

    private ComparisonAnswerPayloadExtractor() {
    }

    public static Map<String, String> extract(
            JsonNode answerPayload
    ) {
        if (answerPayload == null
                || !answerPayload.isObject()
                || answerPayload.size() != 1) {
            throw new IllegalArgumentException(
                    "INVALID_ANSWER_PAYLOAD"
            );
        }

        JsonNode selections =
                answerPayload.get("selections");

        if (selections == null
                || !selections.isArray()
                || selections.isEmpty()) {
            throw new IllegalArgumentException(
                    "INVALID_ANSWER_PAYLOAD"
            );
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (JsonNode selection : selections) {

            if (!selection.isObject()
                    || selection.size() != 2) {
                throw new IllegalArgumentException(
                        "INVALID_ANSWER_PAYLOAD"
                );
            }

            JsonNode comparisonId =
                    selection.get("comparisonId");

            JsonNode selectedOptionId =
                    selection.get("selectedOptionId");

            if (comparisonId == null
                    || !comparisonId.isString()
                    || comparisonId.stringValue().isBlank()
                    || selectedOptionId == null
                    || !selectedOptionId.isString()
                    || selectedOptionId.stringValue().isBlank()) {
                throw new IllegalArgumentException(
                        "INVALID_ANSWER_PAYLOAD"
                );
            }

            String previous = result.putIfAbsent(
                    comparisonId.stringValue(),
                    selectedOptionId.stringValue()
            );

            if (previous != null) {
                throw new IllegalArgumentException(
                        "INVALID_ANSWER_PAYLOAD"
                );
            }
        }

        return Map.copyOf(result);
    }
}
