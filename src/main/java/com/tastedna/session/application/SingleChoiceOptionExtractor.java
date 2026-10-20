package com.tastedna.session.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

public final class SingleChoiceOptionExtractor {

    private SingleChoiceOptionExtractor() {
    }

    public static Set<String> extract(
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
                    || !"SINGLE_CHOICE".equals(kind.stringValue())) {
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

            return Set.copyOf(optionIds);

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
