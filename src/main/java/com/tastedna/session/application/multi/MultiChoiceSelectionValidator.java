package com.tastedna.session.application.multi;

import java.util.List;
import java.util.Set;

// 복수 선택 답변의 선택 개수와 허용 선택지 포함 여부를 검증한다.
// 중복 선택과 배타적 선택지 조건은 상위 단계의 다른 검증기가 담당한다.
public final class MultiChoiceSelectionValidator {

    private MultiChoiceSelectionValidator() {
    }

    // 최소 한 개 이상 선택해야 하며, 질문에 정의된 최대 선택 개수를 초과할 수 없다.
    // 선택한 옵션 ID는 모두 허용 선택지 집합에 포함되어야 한다.
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
