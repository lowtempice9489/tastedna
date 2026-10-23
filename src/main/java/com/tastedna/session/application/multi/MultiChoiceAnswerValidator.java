package com.tastedna.session.application.multi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MultiChoiceAnswerValidator {

    private MultiChoiceAnswerValidator() {
    }

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
