package com.tastedna.scoring.application;

import java.util.Set;

public record ResultEvidenceAssessment(
        Set<String> eligibleCandidates,
        Set<String> unavailableEvidenceQuestions,
        boolean finalResultReady
) {

    // 현재는 Q02 직접 관측만 후보 추출에 사용한다.
    // 시각적 근거가 미확정이면 최종 RESULT 판별을 완료하지 않는다.
    public static ResultEvidenceAssessment initial(
            Set<String> eligibleCandidates
    ) {
        return new ResultEvidenceAssessment(
                Set.copyOf(eligibleCandidates),
                Set.of("Q05", "Q08", "Q09", "Q10"),
                false
        );
    }
}
