package com.tastedna.session.application.comparison;

import tools.jackson.databind.JsonNode;
import java.util.*;

// 비교 그룹별 선택 방식과 배열 순서를 보존한다. 정의와의 일치는 검증기가 확인한다.
public final class ComparisonAnswerPayloadExtractor {
    private ComparisonAnswerPayloadExtractor() {}

    public record Selection(boolean ranked, List<String> optionIds) {
        public Selection { optionIds = List.copyOf(optionIds); }

        // 순위형으로 검증된 그룹에만 사용한다. 일반 비교의 대안에는 부정 의미가 없다.
        public List<String> notPreferredIds(JsonNode group) {
            if (!ranked) return List.of();
            List<String> result = new ArrayList<>();
            for (JsonNode option : group.path("options")) {
                String id = option.path("id").asString();
                if (!optionIds.contains(id)) result.add(id);
            }
            return List.copyOf(result);
        }
    }

    // 기존 단일 비교 호출 계약을 유지한다. 순위형을 한 항목으로 축약하지 않는다.
    public static Map<String, String> extract(JsonNode payload) {
        Map<String, String> result = new LinkedHashMap<>();
        extractSelections(payload).forEach((id, selection) -> {
            if (selection.ranked()) throw invalid();
            result.put(id, selection.optionIds().getFirst());
        });
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, Selection> extractSelections(JsonNode payload) {
        if (payload == null || !payload.isObject() || payload.size() != 1) throw invalid();
        JsonNode selections = payload.get("selections");
        if (selections == null || !selections.isArray() || selections.isEmpty()) throw invalid();
        Map<String, Selection> result = new LinkedHashMap<>();
        for (JsonNode selection : selections) {
            if (!selection.isObject() || selection.size() != 2) throw invalid();
            String id = text(selection.get("comparisonId"));
            boolean ranked = selection.has("selectedOptionIds");
            List<String> ids = new ArrayList<>();
            if (ranked) {
                JsonNode array = selection.get("selectedOptionIds");
                if (!array.isArray()) throw invalid();
                for (JsonNode option : array) ids.add(text(option));
                if (new HashSet<>(ids).size() != ids.size()) throw invalid();
            } else ids.add(text(selection.get("selectedOptionId")));
            if (result.putIfAbsent(id, new Selection(ranked, ids)) != null) throw invalid();
        }
        return Collections.unmodifiableMap(result);
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isString() || node.stringValue().isBlank()) throw invalid();
        return node.stringValue();
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("INVALID_ANSWER_PAYLOAD");
    }
}
