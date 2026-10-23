package com.tastedna.scoring;

import com.tastedna.scoring.application.VisualEvidenceEligibility;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class VisualEvidenceEligibilityTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    // 이미지와 후보 연결 정보가 갖춰졌는지 검사하는 공통 테스트 도우미.
    // 실제 이미지 분석이 아니라 메타데이터 사용 가능 여부만 확인한다.
    private boolean eligible(
            String optionJson,
            String mappingsJson
    ) throws Exception {

        JsonNode option = mapper.readTree(optionJson);
        JsonNode mappings = mapper.readTree(mappingsJson);

        return VisualEvidenceEligibility.isEligible(
                option,
                mappings
        );
    }

    @Test
    void rejectsMissingVisualAsset() throws Exception {

        assertFalse(eligible("""
                {"visualAssetId": null}
                """, """
                [{"candidateId":"R01","verified":true}]
                """));
    }

    @Test
    void rejectsEmptyCandidateMappings() throws Exception {

        assertFalse(eligible("""
                {"visualAssetId":"OUTFIT-001"}
                """, """
                []
                """));
    }

    @Test
    void rejectsUnverifiedCandidateMapping() throws Exception {

        assertFalse(eligible("""
                {"visualAssetId":"OUTFIT-001"}
                """, """
                [{"candidateId":"R01","verified":false}]
                """));
    }

    @Test
    void acceptsStructurallyVerifiedEvidence()
            throws Exception {

        assertTrue(eligible("""
                {"visualAssetId":"OUTFIT-001"}
                """, """
                [{"candidateId":"R01","verified":true}]
                """));
    }

    @Test
    void rejectsMappingWithoutVerificationFlag()
            throws Exception {

        assertFalse(eligible("""
                {"visualAssetId":"OUTFIT-001"}
                """, """
                [{"candidateId":"R01"}]
                """));
    }

    @Test
    void rejectsMixedVerifiedAndUnverifiedMappings()
            throws Exception {

        assertFalse(eligible("""
                {"visualAssetId":"OUTFIT-001"}
                """, """
                [
                  {"candidateId":"R01","verified":true},
                  {"candidateId":"R02","verified":false}
                ]
                """));
    }
}
