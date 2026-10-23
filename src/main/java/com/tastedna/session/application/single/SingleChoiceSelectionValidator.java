package com.tastedna.session.application.single;

import java.util.Set;

// 단일 선택 답변이 질문에 정의된 유효한 선택지인지 확인한다.
// 질문 정의 자체가 잘못된 경우와 사용자의 선택값이 잘못된 경우를 서로 다른 오류로 구분한다.
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
