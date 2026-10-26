package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
}
