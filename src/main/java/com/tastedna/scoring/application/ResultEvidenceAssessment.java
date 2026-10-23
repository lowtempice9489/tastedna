package com.tastedna.scoring.application;

import java.util.Set;

// RESULT 초기 후보와 아직 사용 불가능한 시각적 근거 문항을 함께 전달한다.
// 판별 준비 상태를 표현하는 자료형이며, 최종 RESULT나 후보 순위를 의미하지 않는다.
public record ResultEvidenceAssessment(
        Set<String> eligibleCandidates,
        Set<String> unavailableEvidenceQuestions,
        boolean finalResultReady
) {

    // 초기 후보 목록을 받아 Q05·Q08·Q09·Q10을 미확정 근거로 지정한다.
    // 현재 상태를 고정 반환하는 보조 메서드이며, 실제 메타데이터를 조회하지 않는다.
    public static ResultEvidenceAssessment initial(
            Set<String> eligibleCandidates
    ) {
        // 후보 집합을 복사해 보관하고 미확정 문항 목록과 false 상태를 반환한다.
        // 최종 RESULT를 결정할 판별 규칙이 구현되지 않았으므로 완료 상태로 표시하지 않는다.
        return new ResultEvidenceAssessment(
                Set.copyOf(eligibleCandidates),
                Set.of("Q05", "Q08", "Q09", "Q10"),
                false
        );
    }
}
