package com.tastedna.scoring.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Simple 전체 질문에서 생성된 Raw 관측을 판별 단계로 전달한다.
// 아직 후보별 점수나 선호 강도를 임의로 계산하지 않는다.
public record SimpleObservationProfile(
        Map<String, List<RawObservationAggregator.RawObservation>>
        observationsByType
) {

    // 기존 Raw 관측 구조를 그대로 유지하되 외부 변경을 차단한다.
    public SimpleObservationProfile {
        if (observationsByType == null) {
            throw new IllegalArgumentException(
                    "INVALID_SIMPLE_OBSERVATIONS"
            );
        }

        Map<String, List<RawObservationAggregator.RawObservation>>
                copy = new LinkedHashMap<>();

        observationsByType.forEach((type, observations) -> {
            if (type == null || type.isBlank()
                    || observations == null) {
                throw new IllegalArgumentException(
                        "INVALID_SIMPLE_OBSERVATIONS"
                );
            }

            copy.put(type, List.copyOf(observations));
        });

        observationsByType = Map.copyOf(copy);
    }

    // 실제 관측이 존재하는 질문 ID만 반환한다.
    // 응답하지 않은 질문을 선호 또는 비선호로 추정하지 않는다.
    public Set<String> observedQuestionIds() {
        return observationsByType.values().stream()
                .flatMap(List::stream)
                .map(RawObservationAggregator.RawObservation::questionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    // 특정 관측 유형의 원본 근거를 반환한다.
    // 관측이 없다면 빈 목록을 반환하며 부정 증거를 생성하지 않는다.
    public List<RawObservationAggregator.RawObservation> observationsOf(
            String observationType
    ) {
        return observationsByType.getOrDefault(
                observationType,
                List.of()
        );
    }
}
