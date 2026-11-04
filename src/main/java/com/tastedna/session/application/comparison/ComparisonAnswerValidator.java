package com.tastedna.session.application.comparison;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

// 정의에 명시된 비교 그룹과 조건부 후속 질문을 검증한다.
// 기존 A/B 계약도 그대로 허용하며 비활성 후속 답변은 거부한다.
public final class ComparisonAnswerValidator {
    private ComparisonAnswerValidator() {}

    public static void validate(String questionPayload, JsonNode answerPayload, JsonMapper mapper) {
        var selections = ComparisonAnswerPayloadExtractor.extractSelections(answerPayload);
        JsonNode root = mapper.readTree(questionPayload);
        List<JsonNode> active = activeComparisons(root, selections);
        Set<String> required = new LinkedHashSet<>();
        for (JsonNode comparison : active) {
            String id = comparison.path("id").asString();
            required.add(id);
            var selection = selections.get(id);
            boolean ranked = isRanked(comparison);
            if (selection == null || selection.ranked() != ranked)
                throw new IllegalArgumentException("INVALID_COMPARISON_SELECTION");
            Set<String> allowed = new HashSet<>();
            for (JsonNode option : comparison.path("options")) allowed.add(option.path("id").asString());
            if (!allowed.containsAll(selection.optionIds())
                    || (ranked && selection.optionIds().size() < comparison.path("minSelections").asInt()))
                throw new IllegalArgumentException("INVALID_COMPARISON_SELECTION");
        }
        if (!selections.keySet().equals(required))
            throw new IllegalArgumentException("INVALID_COMPARISON_SELECTION");
    }

    public static List<JsonNode> activeComparisons(JsonNode root, Map<String, ComparisonAnswerPayloadExtractor.Selection> selections) {
        if (root == null || !root.isObject() || !"COMPARISON".equals(root.path("kind").asString())
                || !root.path("comparisons").isArray() || root.path("comparisons").isEmpty())
            throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
        Map<String, Set<String>> preceding = new LinkedHashMap<>();
        Set<String> activeIds = new HashSet<>();
        Set<String> rankedIds = new HashSet<>();
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
            if (isRanked(group)) {
                // 최소 선택 수는 제품 정책이므로 기본값을 만들지 않고 정의에 명시하도록 한다.
                JsonNode minimum = group.get("minSelections");
                if (minimum == null || !minimum.isIntegralNumber() || !minimum.canConvertToInt()
                        || minimum.asInt() < 0 || minimum.asInt() > options.size())
                    throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
                rankedIds.add(id);
            }
            boolean enabled = true;
            JsonNode condition = group.get("showWhen");
            if (condition != null) {
                String parent = text(condition.get("comparisonId"));
                JsonNode triggers = condition.path("optionIds");
                // 순위형 부모의 조건부 의미는 미정이다. 기존 단일 부모 조건만 유지한다.
                if (!preceding.containsKey(parent) || rankedIds.contains(parent) || !triggers.isArray() || triggers.isEmpty())
                    throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
                enabled = false;
                Set<String> unique = new HashSet<>();
                for (JsonNode trigger : triggers) {
                    String value = text(trigger);
                    if (!preceding.get(parent).contains(value) || !unique.add(value))
                        throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
                    var selected = selections.get(parent);
                    if (activeIds.contains(parent) && selected != null && !selected.ranked()
                            && selected.optionIds().contains(value)) enabled = true;
                }
            }
            preceding.put(id, optionIds);
            if (enabled) { active.add(group); activeIds.add(id); }
        }
        return List.copyOf(active);
    }

    public static boolean isRanked(JsonNode group) {
        JsonNode mode = group.get("selectionMode");
        if (mode == null) return false;
        if (mode.isString() && "RANKED_MULTI_SELECT".equals(mode.stringValue())) return true;
        if (mode.isString() && "SINGLE".equals(mode.stringValue())) return false;
        throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
    }

    private static String text(JsonNode value) {
        if (value == null || !value.isString() || value.stringValue().isBlank())
            throw new IllegalArgumentException("INVALID_COMPARISON_DEFINITION");
        return value.stringValue();
    }
}
