package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;

import java.util.*;

// Simple 사용자의 직접 관측과 실험용 RESULT 후보 속성 매핑을 비교한다.
// 일치하는 선택지 근거만 반환하며, 후보 점수나 최종 RESULT를 계산하지 않는다.
public final class SimpleCandidateAttributeMatcher {

    private SimpleCandidateAttributeMatcher() {
    }

    public record AttributeMatch(
            String questionId,
            String optionId,
            String reason,
            long answerRevision
    ) {
    }

    public record CandidateMatches(
            String candidateId,
            List<AttributeMatch> matches
    ) {
        public CandidateMatches {
            matches = List.copyOf(matches);
        }
    }

    // 관측된 선택지만 후보별 매핑과 비교한다.
    // 응답하지 않은 질문이나 선택하지 않은 항목을 부정 근거로 해석하지 않는다.
    public static List<CandidateMatches> match(
            SimpleObservationProfile profile,
            JsonNode mapping
    ) {
        if (profile == null
                || mapping == null
                || !mapping.isObject()
                || !"EXPERIMENTAL".equals(
                mapping.path("status").stringValue())) {
            throw new IllegalArgumentException(
                    "INVALID_ATTRIBUTE_MAPPING"
            );
        }

        JsonNode candidateProfiles =
                mapping.path("candidateProfiles");

        if (!candidateProfiles.isArray()) {
            throw new IllegalArgumentException(
                    "INVALID_ATTRIBUTE_MAPPING"
            );
        }

        List<CandidateMatches> results = new ArrayList<>();
        Set<String> observedQuestions =
                profile.observedQuestionIds();

        for (JsonNode candidate : candidateProfiles) {
            String candidateId =
                    candidate.path("candidateId").stringValue();

            List<AttributeMatch> matches = new ArrayList<>();

            for (JsonNode selection :
                    candidate.path("supportingSelections")) {

                String questionId =
                        selection.path("questionId").stringValue();

                String optionId =
                        selection.path("optionId").stringValue();

                if (!observedQuestions.contains(questionId)) {
                    continue;
                }

                for (var observations :
                        profile.observationsByType().values()) {

                    for (var observation : observations) {

                        if (questionId.equals(observation.questionId())
                                && optionId.equals(observation.optionId())
                                && "EXPLICIT_SELECTION".equals(
                                observation.evidence())) {

                            matches.add(new AttributeMatch(
                                    questionId,
                                    optionId,
                                    selection.path("reason").stringValue(),
                                    observation.answerRevision()
                            ));
                        }
                    }
                }
            }

            results.add(new CandidateMatches(
                    candidateId,
                    matches
            ));
        }

        return List.copyOf(results);
    }

    // 후보 간 공통 근거와 후보별 차별 근거를 구분한 실험 결과다.
    // 이 결과는 후보 점수, 순위 또는 최종 RESULT를 의미하지 않는다.
    public record EvidenceComparison(
            Set<String> commonSelections,
            Map<String, Set<String>> distinctiveSelections
    ) {
        public EvidenceComparison {
            commonSelections = Set.copyOf(commonSelections);

            Map<String, Set<String>> copy = new LinkedHashMap<>();

            distinctiveSelections.forEach((candidateId, selections) ->
                    copy.put(candidateId, Set.copyOf(selections))
            );

            distinctiveSelections = Map.copyOf(copy);
        }
    }

    // 동일한 질문·선택지 조합이 여러 후보에 일치하는지 비교한다.
    // 실험용 매핑의 근거가 서로 얼마나 겹치는지 확인하는 용도로 사용한다.
    public static EvidenceComparison compareEvidence(
            List<CandidateMatches> candidateMatches
    ) {
        if (candidateMatches == null) {
            throw new IllegalArgumentException(
                    "INVALID_CANDIDATE_MATCHES"
            );
        }

        Map<String, Set<String>> selectionsByCandidate =
                new LinkedHashMap<>();

        Map<String, Integer> candidateCounts =
                new LinkedHashMap<>();

        for (CandidateMatches candidate : candidateMatches) {
            if (candidate == null
                    || candidate.candidateId() == null
                    || candidate.candidateId().isBlank()
                    || selectionsByCandidate.containsKey(
                    candidate.candidateId())) {
                throw new IllegalArgumentException(
                        "INVALID_CANDIDATE_MATCHES"
                );
            }

            Set<String> selections = new LinkedHashSet<>();

            for (AttributeMatch match : candidate.matches()) {
                selections.add(
                        match.questionId() + ":" + match.optionId()
                );
            }

            selectionsByCandidate.put(
                    candidate.candidateId(),
                    selections
            );

            // 같은 후보에서 중복된 선택은 한 번만 계산한다.
            for (String selection : selections) {
                candidateCounts.merge(
                        selection,
                        1,
                        Integer::sum
                );
            }
        }

        Set<String> common = new LinkedHashSet<>();
        Map<String, Set<String>> distinctive =
                new LinkedHashMap<>();

        // 둘 이상의 후보에 등장하면 공통 근거로 분류한다.
        // 어느 후보에도 우선권을 부여하지 않는다.
        candidateCounts.forEach((selection, count) -> {
            if (count > 1) {
                common.add(selection);
            }
        });

        selectionsByCandidate.forEach((candidateId, selections) -> {
            Set<String> unique = new LinkedHashSet<>();

            for (String selection : selections) {
                if (candidateCounts.get(selection) == 1) {
                    unique.add(selection);
                }
            }

            distinctive.put(candidateId, unique);
        });

        return new EvidenceComparison(
                common,
                distinctive
        );
    }

}
