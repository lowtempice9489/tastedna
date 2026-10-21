package com.tastedna.session.application.multi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

public final class MultiChoiceOptionExtractor {

    private MultiChoiceOptionExtractor() {
    }

    public record OptionDefinition(
            Set<String> optionIds,
            int maxSelections
    ) {
    }

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
