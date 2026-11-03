package com.tastedna.session.application.comparison;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

// 정의에 명시된 비교 그룹과 조건부 후속 질문을 검증한다.
// 기존 A/B 계약도 그대로 허용하며 비활성 후속 답변은 거부한다.
public final class ComparisonAnswerValidator {
    private ComparisonAnswerValidator() {}

    public static void validate(String questionPayload, JsonNode answerPayload, JsonMapper mapper) {
        var selections = ComparisonAnswerPayloadExtractor.extract(answerPayload);
        JsonNode root = mapper.readTree(questionPayload);
        List<JsonNode> active = activeComparisons(root, selections);
        Set<String> required = new LinkedHashSet<>();
        for (JsonNode comparison : active) {
            String id = comparison.path("id").asString();
            required.add(id);
            boolean found = false;
            for (JsonNode option : comparison.path("options")) {
                if (option.path("id").asString().equals(selections.get(id))) found = true;
            }
            if (!found) throw new IllegalArgumentException("INVALID_COMPARISON_SELECTION");
        }
        if (!selections.keySet().equals(required))
            throw new IllegalArgumentException("INVALID_COMPARISON_SELECTION");
    }

    public static List<JsonNode> activeComparisons(JsonNode root, Map<String, String> selections) {
        if (root == null || !root.isObject() || !"COMPARISON".equals(root.path("kind").asString())
                || !root.path("comparisons").isArray() || root.path("comparisons").isEmpty())
            throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
        Map<String, Set<String>> preceding = new LinkedHashMap<>();
        Set<String> activeIds = new HashSet<>();
        List<JsonNode> active = new ArrayList<>();
        for (JsonNode group : root.path("comparisons")) {
            String id = text(group.get("id"));
            JsonNode options = group.path("options");
            if (!options.isArray() || options.size() < 2 || preceding.containsKey(id))
                throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
            Set<String> optionIds = new HashSet<>();
            for (JsonNode option : options)
                if (!optionIds.add(text(option.get("id"))))
                    throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
            boolean enabled = true;
            JsonNode condition = group.get("showWhen");
            if (condition != null) {
                String parent = text(condition.get("comparisonId"));
                JsonNode triggers = condition.path("optionIds");
                if (!preceding.containsKey(parent) || !triggers.isArray() || triggers.isEmpty())
                    throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
                enabled = false;
                Set<String> unique = new HashSet<>();
                for (JsonNode trigger : triggers) {
                    String value = text(trigger);
                    if (!preceding.get(parent).contains(value) || !unique.add(value))
                        throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
                    if (activeIds.contains(parent) && value.equals(selections.get(parent))) enabled = true;
                }
            }
            preceding.put(id, optionIds);
            if (enabled) { active.add(group); activeIds.add(id); }
        }
        return List.copyOf(active);
    }

    private static String text(JsonNode value) {
        if (value == null || !value.isString() || value.stringValue().isBlank())
            throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
        return value.stringValue();
    }
}
