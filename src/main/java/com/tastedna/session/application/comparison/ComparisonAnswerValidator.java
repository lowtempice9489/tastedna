package com.tastedna.session.application.comparison;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ComparisonAnswerValidator {

    private ComparisonAnswerValidator() {
    }

    public static void validate(
            String questionPayload,
            JsonNode answerPayload,
            JsonMapper mapper
    ) {
        Map<String, String> selections =
                ComparisonAnswerPayloadExtractor.extract(answerPayload);

        JsonNode root;

        try {
            root = mapper.readTree(questionPayload);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "INVALID_COMPARISON_DEFINITION", e
            );
        }

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "INVALID_COMPARISON_DEFINITION"
            );
        }

        JsonNode kind = root.get("kind");

        if (kind == null || !kind.isString()
                || !"COMPARISON".equals(kind.stringValue())) {
            throw new IllegalArgumentException(
                    "INVALID_COMPARISON_DEFINITION"
            );
        }

        JsonNode comparisons = root.get("comparisons");

        if (comparisons == null || !comparisons.isArray()
                || comparisons.size() != 3) {
            throw new IllegalArgumentException(
                    "INVALID_COMPARISON_DEFINITION"
            );
        }

        Map<String, Set<String>> allowed = new HashMap<>();

        for (JsonNode comparison : comparisons) {
            if (!comparison.isObject()) {
                throw new IllegalArgumentException(
                        "INVALID_COMPARISON_DEFINITION"
                );
            }

            JsonNode id = comparison.get("id");
            JsonNode options = comparison.get("options");

            if (id == null || !id.isString()
                    || id.stringValue().isBlank()
                    || options == null || !options.isArray()
                    || options.size() != 2) {
                throw new IllegalArgumentException(
                        "INVALID_COMPARISON_DEFINITION"
                );
            }

            Set<String> optionIds = new HashSet<>();

            for (JsonNode option : options) {
                JsonNode optionId = option.get("id");

                if (!option.isObject()
                        || optionId == null
                        || !optionId.isString()
                        || optionId.stringValue().isBlank()
                        || !optionIds.add(optionId.stringValue())) {
                    throw new IllegalArgumentException(
                            "INVALID_COMPARISON_DEFINITION"
                    );
                }
            }

            if (allowed.putIfAbsent(
                    id.stringValue(), optionIds
            ) != null) {
                throw new IllegalArgumentException(
                        "INVALID_COMPARISON_DEFINITION"
                );
            }
        }

        if (!selections.keySet().equals(allowed.keySet())) {
            throw new IllegalArgumentException(
                    "INVALID_COMPARISON_SELECTION"
            );
        }

        for (var entry : selections.entrySet()) {
            if (!allowed.get(entry.getKey())
                    .contains(entry.getValue())) {
                throw new IllegalArgumentException(
                        "INVALID_COMPARISON_SELECTION"
                );
            }
        }
    }
}
