package com.tastedna.scoring;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Simple Q02의 실제 선택지와 RESULT 초기 후보 매핑 설정의 일치 여부를 검증한다.
// 질문이나 후보 구성이 변경될 때 누락·중복 매핑을 발견하기 위한 테스트다.
class StyleCoreEvidenceMappingTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    // Q02 선택지 12개가 후보 매핑에 빠짐없이 한 번씩 연결되는지 확인한다.
    // 매핑 설정은 후보 근거만 표현해야 하며 점수나 최종 RESULT를 결정해서는 안 된다.
    @Test
    void mappingMatchesActualQ02Options() throws Exception {

        // 실제 질문 정의와 RESULT 매핑 설정을 각각 읽어 비교한다.
        // 테스트용으로 임의 생성한 선택지가 아닌 프로젝트 fixtures를 검증 기준으로 사용한다.
        JsonNode question = mapper.readTree(
                Files.readString(Path.of(
                        "dev", "fixtures", "simple",
                        "simple_q02_question_payload.json"
                ))
        );

        JsonNode mapping = mapper.readTree(
                Files.readString(Path.of(
                        "dev", "fixtures", "result",
                        "style_core_direct_evidence_v0.1.json"
                ))
        );

        assertEquals(
                "MULTIPLE_CHOICE",
                question.get("kind").stringValue()
        );

        assertEquals(
                "CANDIDATE_EVIDENCE_ONLY",
                mapping.get("status").stringValue()
        );

        assertEquals(
                "Q02",
                mapping.get("sourceQuestionId").stringValue()
        );

        assertEquals(
                "DIRECT_FASHION_LOOK_PREFERENCE",
                mapping.get("sourceObservationType").stringValue()
        );

        Set<String> questionOptionIds = new HashSet<>();

        for (JsonNode option : question.get("options")) {
            assertTrue(questionOptionIds.add(
                    option.get("id").stringValue()
            ));
        }

        Set<String> mappedOptionIds = new HashSet<>();
        Set<String> candidateIds = new HashSet<>();

        // 선택지 ID와 후보 ID에 중복이 없는지 검사한다.
        // 후보 ID가 비어 있는 경우도 잘못된 매핑으로 판단한다.
        for (JsonNode rule : mapping.get("rules")) {

            String optionId =
                    rule.get("optionId").stringValue();

            String candidateId =
                    rule.get("candidateId").stringValue();

            assertTrue(
                    mappedOptionIds.add(optionId),
                    "Duplicate optionId: " + optionId
            );

            assertFalse(
                    candidateId.isBlank(),
                    "Blank candidateId"
            );

            assertTrue(
                    candidateIds.add(candidateId),
                    "Duplicate candidateId: " + candidateId
            );
        }

        assertEquals(12, questionOptionIds.size());
        assertEquals(
                questionOptionIds,
                mappedOptionIds,
                "Q02 options and mapping rules differ"
        );

        // 매핑은 사용자가 명시적으로 선택한 스타일만 후보 근거로 인정하도록 설정돼야 한다.
        // 점수 계산과 최종 RESULT 확정은 별도 판별 규칙이 검증되기 전까지 허용하지 않는다.
        JsonNode constraints = mapping.get("constraints");

        assertTrue(
                constraints.get("requiresExplicitSelection")
                        .booleanValue()
        );

        assertFalse(
                constraints.get("assignsScore").booleanValue()
        );

        assertFalse(
                constraints.get("determinesFinalResult")
                        .booleanValue()
        );
    }
}
