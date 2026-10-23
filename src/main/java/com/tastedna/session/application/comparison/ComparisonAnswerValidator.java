package com.tastedna.session.application.comparison;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// 비교형 질문의 정의와 사용자 선택을 대조해 답변의 유효성을 검사한다.
// 비교쌍마다 지정된 두 선택지 중 하나를 올바르게 선택했는지 확인한다.
public final class ComparisonAnswerValidator {

    private ComparisonAnswerValidator() {
    }

    // 질문에 정의된 비교쌍 3개와 제출된 비교 선택을 검증한다.
    // 비교쌍 ID가 누락되거나 정의되지 않은 선택지가 제출되면 오류로 처리한다.
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

        // 각 비교쌍 ID에 실제 선택 가능한 옵션 ID 두 개를 연결한다.
        // 비교쌍 또는 선택지 ID가 중복되거나 잘못 정의된 경우 검증을 중단한다.
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

        // 제출된 비교쌍이 질문 정의의 비교쌍과 정확히 일치해야 한다.
        // 일부 비교만 제출하거나 존재하지 않는 비교쌍을 추가하는 것을 허용하지 않는다.
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
