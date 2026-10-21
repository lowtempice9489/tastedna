package com.tastedna.session.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

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
