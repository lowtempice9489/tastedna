package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Set;

// 데이터 접근과 분리된 순수 평가기. 호출자가 DB에서 조회한 메타데이터를 전달한다.
public final class VisualEvidenceReadinessEvaluator {
    private VisualEvidenceReadinessEvaluator() {}
    public static Set<String> findUnavailableQuestions(JsonNode outfit, JsonNode comparison, JsonNode combination) {
        Set<String> unavailable = new LinkedHashSet<>();
        for (String id : new String[]{"Q05", "Q10"})
            if (!allOptionsEligible(outfit.path("questions").path(id).path("options"))) unavailable.add(id);
        checkComparison("Q08", comparison, unavailable);
        checkComparison("Q09", combination, unavailable);
        return Set.copyOf(unavailable);
    }
    private static void checkComparison(String id, JsonNode metadata, Set<String> unavailable) {
        JsonNode comparisons = metadata.path("comparisons");
        if (!comparisons.isArray() || comparisons.isEmpty()) { unavailable.add(id); return; }
        for (JsonNode comparison : comparisons)
            if (!allOptionsEligible(comparison.path("options"))) { unavailable.add(id); return; }
    }
    private static boolean allOptionsEligible(JsonNode options) {
        if (!options.isArray() || options.isEmpty()) return false;
        for (JsonNode option : options)
            if (!VisualEvidenceEligibility.isEligible(option, option.path("candidateEvidenceMappings"))) return false;
        return true;
    }
}
