package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;

// 착장 이미지와 RESULT 후보 연결 정보의 사용 가능 여부를 검사한다.
// 현재는 메타데이터의 형식과 검증 표시만 확인하며 실제 이미지 내용은 분석하지 않는다.
public final class VisualEvidenceEligibility {

    private VisualEvidenceEligibility() {
    }

    // 이미지와 스타일 속성이 검증되기 전에는 착장 선택을 RESULT 근거로 사용하지 않는다.
    // 미확정 데이터는 오류로 간주하지 않고, 근거 사용 불가(false)로 처리한다.
    public static boolean isEligible(
            JsonNode optionMetadata,
            JsonNode candidateEvidenceMappings
    ) {
        if (optionMetadata == null
                || !optionMetadata.isObject()
                || candidateEvidenceMappings == null
                || !candidateEvidenceMappings.isArray()
                || candidateEvidenceMappings.isEmpty()) {
            return false;
        }

        // 착장에 연결된 이미지 식별자가 존재하는지 확인한다.
        // 이미지 ID가 없거나 비어 있으면 RESULT 판별 근거로 사용할 수 없다.
        JsonNode visualAssetId =
                optionMetadata.get("visualAssetId");

        if (visualAssetId == null
                || !visualAssetId.isString()
                || visualAssetId.stringValue().isBlank()) {
            return false;
        }

        // 모든 후보 연결에 유효한 candidateId와 verified:true 표시가 있어야 한다.
        // 하나라도 미검증 상태이면 해당 선택지의 근거 사용을 허용하지 않는다.
        for (JsonNode mapping : candidateEvidenceMappings) {

            if (!mapping.isObject()) {
                return false;
            }

            JsonNode candidateId = mapping.get("candidateId");
            JsonNode verified = mapping.get("verified");

            if (candidateId == null
                    || !candidateId.isString()
                    || candidateId.stringValue().isBlank()
                    || verified == null
                    || !verified.isBoolean()
                    || !verified.booleanValue()) {
                return false;
            }
        }

        // 필수 이미지 ID와 후보 매핑의 형식 검사를 통과한 상태다.
        // 실제 이미지와 RESULT 연결의 타당성이 검증됐다는 의미는 아니다.
        return true;
    }
}
