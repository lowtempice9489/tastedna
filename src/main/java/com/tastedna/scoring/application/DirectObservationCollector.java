package com.tastedna.scoring.application;

import com.tastedna.scoring.domain.OptionScoringRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DirectObservationCollector {

    private final SelectedOptionRuleResolver ruleResolver;

    public record DirectObservation(
            String definitionId,
            String questionId,
            String optionId,
            String observationPayload
    ) {
    }

    public List<DirectObservation> collect(
            String definitionId,
            String questionId,
            List<String> selectedOptionIds
    ) {
        Map<String, OptionScoringRule> rules =
                ruleResolver.resolve(
                        definitionId,
                        questionId,
                        selectedOptionIds
                );

        List<DirectObservation> observations =
                new ArrayList<>();

        for (String optionId : selectedOptionIds) {
            OptionScoringRule rule = rules.get(optionId);

            if (rule == null
                    || rule.getObservationPayload() == null
                    || rule.getObservationPayload().isBlank()) {
                throw new IllegalStateException(
                        "OBSERVATION_RULE_NOT_FOUND"
                );
            }

            observations.add(
                    new DirectObservation(
                            definitionId,
                            questionId,
                            optionId,
                            rule.getObservationPayload()
                    )
            );
        }

        return List.copyOf(observations);
    }

    public record ParsedObservation(
            String definitionId,
            String questionId,
            String optionId,
            ObservationPayloadParser.ObservationValue value
    ) {
    }

    public List<ParsedObservation> collectParsed(
            String definitionId,
            String questionId,
            List<String> selectedOptionIds,
            JsonMapper mapper
    ) {
        Objects.requireNonNull(mapper, "mapper");

        return collect(
                definitionId,
                questionId,
                selectedOptionIds
        ).stream()
                .map(observation -> new ParsedObservation(
                        observation.definitionId(),
                        observation.questionId(),
                        observation.optionId(),
                        ObservationPayloadParser.parse(
                                observation.observationPayload(),
                                mapper
                        )
                ))
                .toList();
    }

}
