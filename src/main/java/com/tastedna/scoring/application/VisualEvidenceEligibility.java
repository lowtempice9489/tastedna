package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;

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

        JsonNode visualAssetId =
                optionMetadata.get("visualAssetId");

        if (visualAssetId == null
                || !visualAssetId.isString()
                || visualAssetId.stringValue().isBlank()) {
            return false;
        }

        // 후보 매핑이 존재하더라도 검증되지 않은 연결은 사용하지 않는다.
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

        return true;
    }
}
