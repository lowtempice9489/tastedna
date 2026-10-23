package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class StyleCoreCandidateExtractor {

    private StyleCoreCandidateExtractor() {
    }

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
}
