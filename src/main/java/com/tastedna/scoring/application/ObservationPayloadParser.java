package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class ObservationPayloadParser {

    private ObservationPayloadParser() {
    }

    public record ObservationValue(
            String type,
            String observedValue,
            String evidence
    ) {
    }

    public static ObservationValue parse(
            String observationPayload,
            JsonMapper mapper
    ) {
        if (observationPayload == null
                || observationPayload.isBlank()) {
            throw new IllegalArgumentException(
                    "INVALID_OBSERVATION_PAYLOAD"
            );
        }

        try {
            JsonNode root = mapper.readTree(observationPayload);

            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(
                        "INVALID_OBSERVATION_PAYLOAD"
                );
            }

            String type = requiredText(root, "type");
            String observedValue =
                    requiredText(root, "observedValue");
            String evidence =
                    requiredText(root, "evidence");

            return new ObservationValue(
                    type,
                    observedValue,
                    evidence
            );

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "INVALID_OBSERVATION_PAYLOAD",
                    e
            );
        }
    }

    private static String requiredText(
            JsonNode root,
            String fieldName
    ) {
        JsonNode value = root.get(fieldName);

        if (value == null
                || !value.isString()
                || value.stringValue().isBlank()) {
            throw new IllegalArgumentException(
                    "INVALID_OBSERVATION_PAYLOAD"
            );
        }

        return value.stringValue();
    }
}
