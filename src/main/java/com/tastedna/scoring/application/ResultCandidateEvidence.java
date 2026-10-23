package com.tastedna.scoring.application;

import java.util.List;

// RESULT 후보 하나와 해당 후보를 뒷받침하는 관측 근거를 보관한다.
// 후보 선정 이유를 추적할 수 있도록 질문·선택지·관측 유형을 함께 유지한다.
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

    public ResultCandidateEvidence {
        sources = List.copyOf(sources);
    }
}
