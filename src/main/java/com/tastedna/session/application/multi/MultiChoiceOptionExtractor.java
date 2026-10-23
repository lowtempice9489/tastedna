package com.tastedna.session.application.multi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

// 복수 선택형 질문 정의에서 허용 선택지와 최대 선택 가능 개수를 추출한다.
// 질문 정의의 형식과 선택지 중복을 검증해 잘못된 질문 구성이 사용되지 않도록 한다.
public final class MultiChoiceOptionExtractor {

    private MultiChoiceOptionExtractor() {
    }

    // 복수 선택형 질문에서 허용하는 선택지 ID 집합과 최대 선택 개수를 보관한다.
    // 이후 사용자 답변이 질문의 선택 조건을 만족하는지 검사하는 데 사용한다.
    public record OptionDefinition(
            Set<String> optionIds,
            int maxSelections
    ) {
    }

    // 질문 JSON의 kind, options, maxSelections를 검증해 선택 조건을 구성한다.
    // 최대 선택 개수는 1 이상이며 실제 선택지 수를 초과할 수 없다.
    public static OptionDefinition extract(
            String questionPayload,
            JsonMapper mapper
    ) {
        if (questionPayload == null || mapper == null) {
            throw new IllegalArgumentException(
                    "INVALID_OPTION_DEFINITION"
            );
        }

        try {
            JsonNode root = mapper.readTree(questionPayload);

            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(
                        "INVALID_OPTION_DEFINITION"
                );
            }

            JsonNode kind = root.get("kind");

            if (kind == null
                    || !kind.isString()
                    || !"MULTIPLE_CHOICE".equals(kind.stringValue())) {
                throw new IllegalArgumentException(
                        "INVALID_OPTION_DEFINITION"
                );
            }

            JsonNode options = root.get("options");

            if (options == null
                    || !options.isArray()
                    || options.isEmpty()) {
                throw new IllegalArgumentException(
                        "INVALID_OPTION_DEFINITION"
                );
            }

            JsonNode maxSelectionsNode =
                    root.get("maxSelections");

            if (maxSelectionsNode == null
                    || !maxSelectionsNode.isIntegralNumber()
                    || !maxSelectionsNode.canConvertToInt()) {
                throw new IllegalArgumentException(
                        "INVALID_OPTION_DEFINITION"
                );
            }

            int maxSelections = maxSelectionsNode.intValue();

            if (maxSelections < 1
                    || maxSelections > options.size()) {
                throw new IllegalArgumentException(
                        "INVALID_OPTION_DEFINITION"
                );
            }

            Set<String> optionIds = new HashSet<>();

            for (JsonNode option : options) {

                if (!option.isObject()) {
                    throw new IllegalArgumentException(
                            "INVALID_OPTION_DEFINITION"
                    );
                }

                JsonNode id = option.get("id");

                if (id == null || !id.isString()) {
                    throw new IllegalArgumentException(
                            "INVALID_OPTION_DEFINITION"
                    );
                }

                String optionId = id.stringValue();

                if (optionId.isBlank()
                        || !optionIds.add(optionId)) {
                    throw new IllegalArgumentException(
                            "INVALID_OPTION_DEFINITION"
                    );
                }
            }

            return new OptionDefinition(
                    Set.copyOf(optionIds),
                    maxSelections
            );

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "INVALID_OPTION_DEFINITION",
                    e
            );
        }
    }
}
