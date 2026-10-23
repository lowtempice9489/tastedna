package com.tastedna.session.application.multi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 복수 선택형 질문의 답변을 검증하며, 선택 가능 개수와 배타적 선택 조건을 확인한다.
// 질문 정의와 실제 선택값을 함께 사용해 잘못된 조합의 저장을 방지한다.
public final class MultiChoiceAnswerValidator {

    private MultiChoiceAnswerValidator() {
    }

    // 질문에 정의된 선택지와 최대 선택 개수를 확인한 뒤 제출된 답변을 검사한다.
    // 기본 선택 조건을 통과해도 배타적 선택지 조건은 추가로 검증한다.
    public static void validate(
            String questionPayload,
            JsonNode answerPayload,
            JsonMapper mapper
    ) {
        MultiChoiceOptionExtractor.OptionDefinition definition =
                MultiChoiceOptionExtractor.extract(
                        questionPayload,
                        mapper
                );

        List<String> selectedOptionIds =
                MultiChoiceAnswerPayloadExtractor.extract(
                        answerPayload
                );

        MultiChoiceSelectionValidator.validate(
                selectedOptionIds,
                definition.optionIds(),
                definition.maxSelections()
        );

        validateExclusiveOptions(
                questionPayload,
                selectedOptionIds,
                definition.optionIds(),
                mapper
        );
    }

    // NONE처럼 다른 선택지와 함께 고를 수 없는 배타적 선택지를 검증한다.
    // 배타적 선택지를 다른 옵션과 동시에 제출하면 EXCLUSIVE_OPTION_CONFLICT를 발생시킨다.
    private static void validateExclusiveOptions(
            String questionPayload,
            List<String> selectedOptionIds,
            Set<String> allowedOptionIds,
            JsonMapper mapper
    ) {
        JsonNode question;

        try {
            question = mapper.readTree(questionPayload);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "INVALID_OPTION_DEFINITION",
                    e
            );
        }

        JsonNode exclusiveOptions =
                question.get("exclusiveOptionIds");

        if (exclusiveOptions == null) {
            return;
        }

        if (!exclusiveOptions.isArray()) {
            throw new IllegalArgumentException(
                    "INVALID_OPTION_DEFINITION"
            );
        }

        Set<String> exclusiveIds = new HashSet<>();

        for (JsonNode option : exclusiveOptions) {
            if (!option.isString()
                    || !allowedOptionIds.contains(option.stringValue())
                    || !exclusiveIds.add(option.stringValue())) {
                throw new IllegalArgumentException(
                        "INVALID_OPTION_DEFINITION"
                );
            }
        }

        if (selectedOptionIds.size() > 1) {
            for (String optionId : selectedOptionIds) {
                if (exclusiveIds.contains(optionId)) {
                    throw new IllegalArgumentException(
                            "EXCLUSIVE_OPTION_CONFLICT"
                    );
                }
            }
        }
    }
}
