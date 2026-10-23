package com.tastedna.scoring.application;

import com.tastedna.scoring.domain.OptionScoringRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 사용자가 선택한 옵션을 DB의 관측 규칙과 연결해 직접 관측 데이터를 생성한다.
// 규칙이 없는 선택지는 임의로 해석하지 않으며, RESULT 점수나 최종 결과는 계산하지 않는다.
@Service
@RequiredArgsConstructor
public class DirectObservationCollector {

    private final SelectedOptionRuleResolver ruleResolver;

    // 관측 규칙에서 조회한 원본 데이터를 보관한다.
    // 관측 내용은 아직 JSON 문자열이며, 파싱하기 전 단계의 자료형이다.
    public record DirectObservation(
            String definitionId,
            String questionId,
            String optionId,
            String observationPayload
    ) {
    }

    // 선택된 옵션에 해당하는 관측 규칙을 조회하고 직접 관측 목록을 생성한다.
    // 필수 관측 규칙이 없거나 내용이 비어 있으면 잘못된 데이터로 판단해 처리를 중단한다.
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

    // 원본 관측 JSON을 해석한 결과를 질문 및 선택지 정보와 함께 보관한다.
    // 이후 Raw 관측 집계 단계에서 사용하는 중간 자료형이다.
    public record ParsedObservation(
            String definitionId,
            String questionId,
            String optionId,
            ObservationPayloadParser.ObservationValue value
    ) {
    }

    // collect()로 생성한 관측 데이터를 파싱해 구조화된 관측 목록으로 반환한다.
    // 선택지와 질문의 식별자를 유지해 이후 관측 근거를 추적할 수 있도록 한다.
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
