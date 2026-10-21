package com.tastedna.session.application.single;

import java.util.Set;

public final class SingleChoiceSelectionValidator {

    private SingleChoiceSelectionValidator() {
    }

    public static void validate(
            String selectedOptionId,
            Set<String> allowedOptionIds
    ) {
        if (allowedOptionIds == null
                || allowedOptionIds.isEmpty()
                || allowedOptionIds.stream().anyMatch(
                id -> id == null || id.isBlank()
        )) {
            throw new IllegalArgumentException(
                    "INVALID_OPTION_DEFINITION"
            );
        }

        if (selectedOptionId == null
                || selectedOptionId.isBlank()
                || !allowedOptionIds.contains(selectedOptionId)) {
            throw new IllegalArgumentException(
                    "INVALID_OPTION_SELECTION"
            );
        }
    }
}
