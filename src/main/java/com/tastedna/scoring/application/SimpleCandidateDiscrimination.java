package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Simple RESULT 초기 후보의 구별 가능성과 판별 보류 사유를 표현한다.
// 현재는 검증되지 않은 후보 간 우열을 정하거나 최종 RESULT를 생성하지 않는다.
public record SimpleCandidateDiscrimination(
        Set<String> eligibleCandidates,
        String representativeCandidate,
        String finalResult,
        String reason
) {

    public static final String INSUFFICIENT_EVIDENCE =
            "INSUFFICIENT_DISCRIMINATING_EVIDENCE";

    // 후보 간 판별 규칙이 검증되지 않은 경우 확정을 보류한다.
    // 후보가 하나만 남아 있더라도 자동으로 최종 RESULT로 확정하지 않는다.
    public static SimpleCandidateDiscrimination unresolved(
            Set<String> candidates
    ) {
        return new SimpleCandidateDiscrimination(
                Set.copyOf(candidates),
                null,
                null,
                INSUFFICIENT_EVIDENCE
        );
    }

    // 후보별 실제 관측 근거를 판별 설정과 대조한다.
    // 현재 R01·R02 판별 설정은 미검증 상태이므로 최종 판별은 보류한다.
    public static SimpleCandidateDiscrimination assess(
            List<ResultCandidateEvidence> evidence,
            JsonNode configuration
    ) {
        if (evidence == null
                || configuration == null
                || !configuration.isObject()) {
            throw new IllegalArgumentException(
                    "INVALID_DISCRIMINATION_INPUT"
            );
        }

        JsonNode status = configuration.path("status");
        JsonNode candidatePair = configuration.path("candidatePair");
        JsonNode verifiedQ02 = configuration
                .path("verifiedEvidence")
                .path("Q02");

        if (!status.isString()
                || !candidatePair.isArray()
                || candidatePair.size() != 2
                || !verifiedQ02.isObject()) {
            throw new IllegalArgumentException(
                    "INVALID_DISCRIMINATION_CONFIGURATION"
            );
        }

        // 현재 검증 대상으로 등록된 두 후보만 평가한다.
        Set<String> allowedCandidates = new LinkedHashSet<>();

        for (JsonNode candidate : candidatePair) {
            if (!candidate.isString()
                    || candidate.stringValue().isBlank()
                    || !allowedCandidates.add(candidate.stringValue())) {
                throw new IllegalArgumentException(
                        "INVALID_DISCRIMINATION_CONFIGURATION"
                );
            }
        }

        Set<String> observedCandidates = new LinkedHashSet<>();

        for (ResultCandidateEvidence candidateEvidence : evidence) {
            if (candidateEvidence == null
                    || !allowedCandidates.contains(
                    candidateEvidence.candidateId())
                    || candidateEvidence.sources().isEmpty()
                    || !observedCandidates.add(
                    candidateEvidence.candidateId())) {
                throw new IllegalArgumentException(
                        "INVALID_DISCRIMINATION_EVIDENCE"
                );
            }

            JsonNode verified = verifiedQ02.path(
                    candidateEvidence.candidateId()
            );

            if (!verified.isString()
                    || !"EXPLICIT_POSITIVE".equals(
                    verified.stringValue())) {
                throw new IllegalArgumentException(
                        "UNVERIFIED_DISCRIMINATION_EVIDENCE"
                );
            }

            // 후보별 근거가 실제 Q02 명시적 선택인지 확인한다.
            for (ResultCandidateEvidence.EvidenceSource source
                    : candidateEvidence.sources()) {

                if (source == null
                        || !"Q02".equals(source.questionId())
                        || !candidateEvidence.candidateId()
                        .equals(source.optionId())
                        || !"DIRECT_FASHION_LOOK_PREFERENCE"
                        .equals(source.observationType())
                        || !"EXPLICIT_SELECTION"
                        .equals(source.evidenceType())
                        || source.answerRevision() < 1) {
                    throw new IllegalArgumentException(
                            "INVALID_DISCRIMINATION_EVIDENCE"
                    );
                }
            }
        }

        // 승인되지 않은 판별 규칙으로 후보 순위나 RESULT를 생성하지 않는다.
        if (!"DISCRIMINATION_NOT_VERIFIED".equals(
                status.stringValue())) {
            throw new IllegalStateException(
                    "DISCRIMINATION_RULE_NOT_SUPPORTED"
            );
        }

        return unresolved(observedCandidates);
    }

    public SimpleCandidateDiscrimination {
        eligibleCandidates = Set.copyOf(eligibleCandidates);
    }
}
