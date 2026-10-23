package com.tastedna.session.application.comparison;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

// 비교형 답변 JSON에서 비교쌍 ID와 선택된 옵션 ID의 관계를 추출한다.
// 각 비교쌍의 선택을 독립적으로 보존해 서로 다른 비교 결과가 섞이지 않도록 한다.
public final class ComparisonAnswerPayloadExtractor {

    private ComparisonAnswerPayloadExtractor() {
    }

    // selections 배열을 읽어 comparisonId와 selectedOptionId의 대응 관계를 반환한다.
    // 각 항목은 두 필드만 포함해야 하며 중복 비교쌍 ID나 빈 값은 허용하지 않는다.
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

            // 동일한 comparisonId가 두 번 제출되는 것을 방지한다.
            // 하나의 비교쌍에는 선택 결과가 하나만 존재해야 한다.
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
