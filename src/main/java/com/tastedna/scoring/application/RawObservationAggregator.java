package com.tastedna.scoring.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RawObservationAggregator {

    private RawObservationAggregator() {
    }

    public record RawObservation(
            String definitionId,
            String type,
            String observedValue,
            String evidence,
            String questionId,
            String optionId,
            long answerRevision
    ) {
    }

    public static Map<String, List<RawObservation>> aggregate(
            List<DirectObservationCollector.ParsedObservation> observations,
            Map<String, Long> answerRevisions
    ) {
        if (observations == null || answerRevisions == null) {
            throw new IllegalArgumentException(
                    "INVALID_OBSERVATIONS"
            );
        }

        Map<String, List<RawObservation>> result =
                new LinkedHashMap<>();

        for (var observation : observations) {

            var value = observation.value();

            Long revision = answerRevisions.get(
                    observation.questionId()
            );

            if (revision == null || revision < 1) {
                throw new IllegalStateException(
                        "INVALID_OBSERVATION_REVISION"
                );
            }

            RawObservation raw = new RawObservation(
                    observation.definitionId(),
                    value.type(),
                    value.observedValue(),
                    value.evidence(),
                    observation.questionId(),
                    observation.optionId(),
                    revision
            );

            result.computeIfAbsent(
                    value.type(),
                    ignored -> new ArrayList<>()
            ).add(raw);
        }

        Map<String, List<RawObservation>> immutable =
                new LinkedHashMap<>();

        result.forEach((type, values) ->
                immutable.put(type, List.copyOf(values))
        );

        return Collections.unmodifiableMap(immutable);
    }
}
