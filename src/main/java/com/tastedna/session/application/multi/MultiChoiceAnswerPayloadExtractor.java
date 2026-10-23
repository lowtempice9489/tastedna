package com.tastedna.session.application.multi;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 복수 선택형 답변 JSON에서 사용자가 선택한 옵션 ID 목록을 추출한다.
// 입력 형식과 중복 선택을 검사하며, 허용 선택지 및 최대 개수 검증은 별도 클래스가 담당한다.
public final class MultiChoiceAnswerPayloadExtractor {

    private MultiChoiceAnswerPayloadExtractor() {
    }

    // selectedOptionIds 배열을 읽어 유효한 문자열 목록으로 반환한다.
    // 답변 JSON은 해당 필드 하나만 포함해야 하며 빈 배열은 허용하지 않는다.
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
        // 같은 옵션 ID가 중복 제출되는 것을 방지한다.
        // 사용자가 선택한 옵션의 입력 순서는 반환 목록에 그대로 유지한다.
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
