package com.tastedna.scoring.application;

import java.util.List;

// RESULT 후보와 그 후보를 뒷받침하는 관측 근거 목록을 함께 보관한다.
// 판별 과정의 설명 가능성을 위한 자료형이며, 최종 RESULT 확정이나 점수 계산은 수행하지 않는다.
public record ResultCandidateEvidence(
        String candidateId,
        List<EvidenceSource> sources
) {

    // 특정 RESULT 후보가 어떤 직접 관측으로 도출됐는지 나타낸다.
    // 점수나 최종 판정이 아닌, 검증 가능한 근거 정보만 저장한다.
    public record EvidenceSource(
            String questionId,
            String optionId,
            String observationType,
            String evidenceType,
            long answerRevision
    ) {
    }

    // 전달받은 근거 목록을 복사해 외부에서 항목을 추가하거나 삭제하지 못하도록 한다.
    // 단, 근거 자체의 의미적 타당성이나 중복 여부를 검사하는 기능은 아니다.
    public ResultCandidateEvidence {
        sources = List.copyOf(sources);
    }
}
