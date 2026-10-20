package com.tastedna.session.application;

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
}
