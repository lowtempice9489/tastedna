package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;

import java.util.*;

// 사용자의 직접 스타일 선호 관측을 RESULT 초기 후보에 연결한다.
// 매핑 규칙으로 후보만 추출하며, 점수 계산이나 최종 RESULT 선정은 수행하지 않는다.
public final class StyleCoreCandidateExtractor {

    private StyleCoreCandidateExtractor() {
    }

    // Raw 관측과 후보 매핑 JSON을 받아 조건에 부합하는 후보 ID 집합을 반환한다.
    // 입력 형식과 매핑 규칙을 검증하며, 동일 후보는 중복 반환하지 않는다.
    public static Set<String> extract(
            JsonNode rawObservations,
            JsonNode mapping
    ) {
        if (rawObservations == null
                || !rawObservations.isObject()
                || mapping == null
                || !mapping.isObject()) {
            throw new IllegalArgumentException(
                    "INVALID_CANDIDATE_INPUT"
            );
        }

        JsonNode rules = mapping.get("rules");

        if (rules == null || !rules.isArray()) {
            throw new IllegalArgumentException(
                    "INVALID_CANDIDATE_MAPPING"
            );
        }

        // 매핑 설정의 optionId와 candidateId를 연결하는 조회표를 만든다.
        // 선택지 매핑이 중복되거나 필수 값이 비어 있으면 잘못된 설정으로 처리한다.
        Map<String, String> candidateByOption =
                new LinkedHashMap<>();

        for (JsonNode rule : rules) {
            if (!rule.isObject()) {
                throw new IllegalArgumentException(
                        "INVALID_CANDIDATE_MAPPING"
                );
            }

            JsonNode option = rule.get("optionId");
            JsonNode candidate = rule.get("candidateId");

            if (option == null || !option.isString()
                    || candidate == null || !candidate.isString()
                    || option.stringValue().isBlank()
                    || candidate.stringValue().isBlank()) {
                throw new IllegalArgumentException(
                        "INVALID_CANDIDATE_MAPPING"
                );
            }

            String previous = candidateByOption.putIfAbsent(
                    option.stringValue(),
                    candidate.stringValue()
            );

            if (previous != null) {
                throw new IllegalArgumentException(
                        "DUPLICATE_CANDIDATE_MAPPING"
                );
            }
        }

        // 매핑 파일에 테스트 정의, 원본 질문, 관측 유형 정보가 있는지 확인한다.
        // 현재는 해당 필드의 존재와 문자열 형식만 검사하며, 실제 사용 값과의 일치는 별도 조건에서 확인한다.
        JsonNode sourceDefinition = mapping.get(
                "sourceDefinition"
        );

        JsonNode sourceQuestionId = mapping.get(
                "sourceQuestionId"
        );

        JsonNode sourceObservationType = mapping.get(
                "sourceObservationType"
        );

        if (sourceDefinition == null
                || !sourceDefinition.isString()
                || sourceQuestionId == null
                || !sourceQuestionId.isString()
                || sourceObservationType == null
                || !sourceObservationType.isString()) {
            throw new IllegalArgumentException(
                    "INVALID_CANDIDATE_MAPPING"
            );
        }

        // 현재 구현은 DIRECT_FASHION_LOOK_PREFERENCE 관측만 후보 추출에 사용한다.
        // 다른 관측 유형은 여기서 후보에 반영하지 않으며, 해당 관측이 없으면 빈 결과를 반환한다.
        JsonNode observations = rawObservations.get(
                "DIRECT_FASHION_LOOK_PREFERENCE"
        );

        Set<String> candidates = new LinkedHashSet<>();

        if (observations == null) {
            return Set.of();
        }

        if (!observations.isArray()) {
            throw new IllegalArgumentException(
                    "INVALID_CANDIDATE_INPUT"
            );
        }

        for (JsonNode observation : observations) {
            if (!observation.isObject()) {
                throw new IllegalArgumentException(
                        "INVALID_CANDIDATE_INPUT"
                );
            }

            JsonNode questionId = observation.get("questionId");
            JsonNode optionId = observation.get("optionId");
            JsonNode evidence = observation.get("evidence");

            // 관측의 Definition ID와 매핑 설정의 Definition ID가 일치하는지 검사한다.
            // 서로 다른 테스트 정의의 관측이 섞여 잘못된 후보가 생성되는 것을 방지한다.
            JsonNode expectedDefinitionId = mapping.get(
                    "sourceDefinitionId"
            );

            if (expectedDefinitionId == null
                    || !expectedDefinitionId.isString()
                    || expectedDefinitionId.stringValue().isBlank()) {
                throw new IllegalArgumentException(
                        "INVALID_CANDIDATE_MAPPING"
                );
            }

            JsonNode definitionId = observation.get("definitionId");

            if (definitionId == null
                    || !definitionId.isString()
                    || !expectedDefinitionId.stringValue()
                    .equals(definitionId.stringValue())) {
                throw new IllegalArgumentException(
                        "INVALID_OBSERVATION_DEFINITION"
                );
            }


            if (questionId == null || !questionId.isString()
                    || optionId == null || !optionId.isString()
                    || evidence == null || !evidence.isString()) {
                throw new IllegalArgumentException(
                        "INVALID_CANDIDATE_INPUT"
                );
            }

            // Q02에서 사용자가 명시적으로 선택한 스타일만 초기 후보의 근거로 인정한다.
            // 다른 질문의 관측이나 추론된 선호는 이 단계에서 후보 생성에 사용하지 않는다.
            if (!"Q02".equals(questionId.stringValue())
                    || !"EXPLICIT_SELECTION".equals(
                    evidence.stringValue())) {
                continue;
            }

            String candidateId = candidateByOption.get(
                    optionId.stringValue()
            );

            if (candidateId != null) {
                candidates.add(candidateId);
            }
        }

        return Set.copyOf(candidates);
    }

    // 기존 후보 추출 기준을 유지하면서 후보별 직접 관측 근거를 반환한다.
    // 후보 선정에 사용된 Q02 명시적 선택만 근거로 연결한다.
    public static List<ResultCandidateEvidence> extractWithEvidence(
            JsonNode rawObservations,
            JsonNode mapping
    ) {
        Set<String> candidates = extract(rawObservations, mapping);

        Map<String, String> candidateByOption = new LinkedHashMap<>();

        for (JsonNode rule : mapping.get("rules")) {
            candidateByOption.put(
                    rule.get("optionId").stringValue(),
                    rule.get("candidateId").stringValue()
            );
        }

        Map<String, List<ResultCandidateEvidence.EvidenceSource>>
                sourcesByCandidate = new LinkedHashMap<>();

        for (String candidate : candidates) {
            sourcesByCandidate.put(candidate, new ArrayList<>());
        }

        JsonNode observations = rawObservations.get(
                "DIRECT_FASHION_LOOK_PREFERENCE"
        );

        if (observations != null) {
            for (JsonNode observation : observations) {
                if (!"Q02".equals(
                        observation.get("questionId").stringValue())
                        || !"EXPLICIT_SELECTION".equals(
                        observation.get("evidence").stringValue())) {
                    continue;
                }

                String optionId =
                        observation.get("optionId").stringValue();

                String candidateId =
                        candidateByOption.get(optionId);

                if (candidateId == null) {
                    continue;
                }

                JsonNode revision = observation.get("answerRevision");

                if (revision == null
                        || !revision.canConvertToLong()
                        || revision.longValue() < 1) {
                    throw new IllegalArgumentException(
                            "INVALID_EVIDENCE_REVISION"
                    );
                }

                sourcesByCandidate.get(candidateId).add(
                        new ResultCandidateEvidence.EvidenceSource(
                                observation.get("questionId").stringValue(),
                                optionId,
                                "DIRECT_FASHION_LOOK_PREFERENCE",
                                observation.get("evidence").stringValue(),
                                revision.longValue()
                        )
                );
            }
        }

        List<ResultCandidateEvidence> result = new ArrayList<>();

        sourcesByCandidate.forEach((candidateId, sources) ->
                result.add(new ResultCandidateEvidence(
                        candidateId,
                        sources
                ))
        );

        return List.copyOf(result);
    }

}
