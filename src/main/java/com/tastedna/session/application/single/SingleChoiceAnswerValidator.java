package com.tastedna.session.application.single;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

// 단일 선택형 답변이 실제 질문에 등록된 선택지 중 하나인지 검증한다.
// JSON 답변은 선택지 ID를 추출한 뒤 동일한 검증 로직으로 처리한다.
public final class SingleChoiceAnswerValidator {

    private SingleChoiceAnswerValidator() {
    }

    public static void validate(
            String questionPayload,
            String selectedOptionId,
            JsonMapper mapper
    ) {
        Set<String> allowedOptionIds =
                SingleChoiceOptionExtractor.extract(
                        questionPayload,
                        mapper
                );

        SingleChoiceSelectionValidator.validate(
                selectedOptionId,
                allowedOptionIds
        );
    }

    public static void validate(
            String questionPayload,
            JsonNode answerPayload,
            JsonMapper mapper
    ) {
        String selectedOptionId =
                SingleChoiceAnswerPayloadExtractor.extract(
                        answerPayload
                );

        validate(
                questionPayload,
                selectedOptionId,
                mapper
        );
    }
}
