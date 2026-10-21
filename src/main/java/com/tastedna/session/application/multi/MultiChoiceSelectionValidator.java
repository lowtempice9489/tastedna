package com.tastedna.session.application.multi;

import java.util.List;
import java.util.Set;

public final class MultiChoiceSelectionValidator {

    private MultiChoiceSelectionValidator() {
    }

    public static void validate(
            List<String> selectedOptionIds,
            Set<String> allowedOptionIds,
            int maxSelections
    ) {

        if (selectedOptionIds == null
                || selectedOptionIds.isEmpty()
                || allowedOptionIds == null
                || allowedOptionIds.isEmpty()
                || maxSelections < 1) {

            throw new IllegalArgumentException(
                    "INVALID_OPTION_DEFINITION"
            );
        }

        if (selectedOptionIds.size() > maxSelections) {
            throw new IllegalArgumentException(
                    "TOO_MANY_OPTIONS_SELECTED"
            );
        }

        for (String optionId : selectedOptionIds) {
            if (!allowedOptionIds.contains(optionId)) {
                throw new IllegalArgumentException(
                        "INVALID_OPTION_SELECTION"
                );
            }
        }
    }
}
