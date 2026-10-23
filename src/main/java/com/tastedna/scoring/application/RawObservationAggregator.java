package com.tastedna.scoring.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 수집된 직접 관측을 관측 유형별로 묶어 Raw 관측 결과를 생성한다.
// 각 관측의 출처와 답변 Revision을 유지하며, RESULT 점수 계산은 수행하지 않는다.
public final class RawObservationAggregator {

    private RawObservationAggregator() {
    }

    // 관측값과 근거 유형뿐 아니라 테스트 정의, 질문, 선택지, 답변 Revision을 함께 보관한다.
    // RESULT 후보가 어떤 사용자 응답에서 유래했는지 추적하는 데 필요한 자료형이다.
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

    // 파싱된 직접 관측들을 유형별로 분류하고 Raw 관측 목록으로 반환한다.
    // 관측 데이터 또는 Revision 정보가 없으면 정상적인 집계가 불가능하므로 오류로 처리한다.
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

            // 관측이 생성된 질문의 답변 Revision을 확인한다.
            // Revision이 없거나 유효하지 않으면 관측 근거를 추적할 수 없어 처리를 중단한다.
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

            // 같은 관측 유형(type)을 하나의 목록으로 묶는다.
            // 질문이나 선택지가 달라도 관측 유형이 같으면 동일한 그룹에 포함한다.
            result.computeIfAbsent(
                    value.type(),
                    ignored -> new ArrayList<>()
            ).add(raw);
        }

        // 반환된 관측 목록과 Map의 구성을 외부 코드가 변경하지 못하도록 보호한다.
        // 집계가 완료된 Raw 관측 데이터를 후속 단계에서 안전하게 참조하도록 한다.
        Map<String, List<RawObservation>> immutable =
                new LinkedHashMap<>();

        result.forEach((type, values) ->
                immutable.put(type, List.copyOf(values))
        );

        return Collections.unmodifiableMap(immutable);
    }
}
