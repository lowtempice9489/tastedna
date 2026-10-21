
package com.tastedna.session.application.multi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

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
    }
}
