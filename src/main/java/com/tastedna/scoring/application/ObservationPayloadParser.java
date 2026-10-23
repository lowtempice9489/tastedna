package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// DB에 저장된 관측 JSON을 해석해 구조화된 관측 데이터로 변환한다.
// 필수 항목이 누락되거나 형식이 잘못된 데이터는 오류로 처리한다.
public final class ObservationPayloadParser {

    private ObservationPayloadParser() {
    }

    // 파싱된 관측의 유형, 실제 관측값, 근거 유형을 보관한다.
    // 원본 JSON 대신 후속 관측 처리 단계에서 사용하는 자료형이다.
    public record ObservationValue(
            String type,
            String observedValue,
            String evidence
    ) {
    }

    // 관측 JSON 문자열을 읽어 ObservationValue 객체로 변환한다.
    // 입력이 비었거나 필수 데이터가 올바르지 않으면 예외를 발생시킨다.
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

            // 관측 유형(type), 관측값(observedValue), 근거(evidence)는 모두 필수다.
            // 세 항목 중 하나라도 없으면 불완전한 관측으로 판단한다.
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
    // JSON에서 지정한 필드를 문자열로 추출하고 필수 조건을 검사한다.
    // 값이 없거나 문자열이 아니거나 빈 문자열이면 오류를 발생시킨다.
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
