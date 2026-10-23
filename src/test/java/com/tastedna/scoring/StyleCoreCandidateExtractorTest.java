package com.tastedna.scoring;

import com.tastedna.scoring.application.StyleCoreCandidateExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Q02의 명시적 스타일 선택 관측만 RESULT 초기 후보로 추출하는지 검증한다.
// 다른 질문이나 추론 관측이 후보에 섞이지 않도록 판별 경계를 보호한다.
class StyleCoreCandidateExtractorTest {

    private static final String DEFINITION_ID =
            "22222222-2222-4222-8222-222222222222";

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    private JsonNode loadMapping() throws Exception {
        return mapper.readTree(
                Files.readString(Path.of(
                        "dev", "fixtures", "result",
                        "style_core_direct_evidence_v0.1.json"
                ))
        );
    }

    // Q02에서 명시적으로 선택한 R01과 R02가 초기 후보로 추출되는지 확인한다.
    // 후보 추출은 점수 계산이나 최종 RESULT 확정을 의미하지 않는다.
    @Test
    void extractsCandidatesFromActualQ02Observation()
            throws Exception {

        JsonNode observations = mapper.readTree("""
                {
                  "DIRECT_FASHION_LOOK_PREFERENCE": [
                    {
                      "definitionId": "%s",
                      "questionId": "Q02",
                      "optionId": "R01",
                      "evidence": "EXPLICIT_SELECTION"
                    },
                    {
                      "definitionId": "%s",
                      "questionId": "Q02",
                      "optionId": "R02",
                      "evidence": "EXPLICIT_SELECTION"
                    }
                  ]
                }
                """.formatted(DEFINITION_ID, DEFINITION_ID));

        Set<String> candidates =
                StyleCoreCandidateExtractor.extract(
                        observations,
                        loadMapping()
                );

        assertEquals(
                Set.of("R01", "R02"),
                candidates
        );
    }

    // 추론 관측이나 Q02 이외 질문에서 발생한 관측은 초기 후보 근거로 인정하지 않는다.
    // 명시적 선택이라는 근거 조건이 유지되는지 검증한다.
    @Test
    void ignoresObservationsWithoutExplicitQ02Evidence()
            throws Exception {

        JsonNode observations = mapper.readTree("""
                {
                  "DIRECT_FASHION_LOOK_PREFERENCE": [
                    {
                      "definitionId": "%s",
                      "questionId": "Q02",
                      "optionId": "R01",
                      "evidence": "INFERRED"
                    },
                    {
                      "definitionId": "%s",
                      "questionId": "Q03",
                      "optionId": "R02",
                      "evidence": "EXPLICIT_SELECTION"
                    }
                  ]
                }
                """.formatted(DEFINITION_ID, DEFINITION_ID));

        assertTrue(
                StyleCoreCandidateExtractor.extract(
                        observations,
                        loadMapping()
                ).isEmpty()
        );
    }

    @Test
    void returnsEmptyWhenFashionLookObservationIsAbsent()
            throws Exception {

        JsonNode observations = mapper.readTree("""
                {
                  "DIRECT_COLOR_PREFERENCE": []
                }
                """);

        assertTrue(
                StyleCoreCandidateExtractor.extract(
                        observations,
                        loadMapping()
                ).isEmpty()
        );
    }

    @Test
    void rejectsInvalidObservationStructure()
            throws Exception {

        JsonNode observations = mapper.readTree("""
                {
                  "DIRECT_FASHION_LOOK_PREFERENCE": [
                    {
                      "definitionId": "%s",
                      "questionId": "Q02",
                      "optionId": "R01"
                    }
                  ]
                }
                """.formatted(DEFINITION_ID));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StyleCoreCandidateExtractor.extract(
                                observations,
                                loadMapping()
                        )
                );

        assertEquals(
                "INVALID_CANDIDATE_INPUT",
                exception.getMessage()
        );
    }

    // 후보 매핑이 기대하는 테스트 정의와 다른 definitionId의 관측은 거부한다.
    // 서로 다른 테스트 버전의 관측이 동일한 후보 판별에 혼합되는 것을 방지한다.
    @Test
    void rejectsDifferentDefinitionId() throws Exception {

        JsonNode observations = mapper.readTree("""
                {
                  "DIRECT_FASHION_LOOK_PREFERENCE": [
                    {
                      "definitionId": "11111111-1111-4111-8111-111111111111",
                      "questionId": "Q02",
                      "optionId": "R01",
                      "evidence": "EXPLICIT_SELECTION"
                    }
                  ]
                }
                """);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StyleCoreCandidateExtractor.extract(
                                observations,
                                loadMapping()
                        )
                );

        assertEquals(
                "INVALID_OBSERVATION_DEFINITION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMissingDefinitionId() throws Exception {

        JsonNode observations = mapper.readTree("""
                {
                  "DIRECT_FASHION_LOOK_PREFERENCE": [
                    {
                      "questionId": "Q02",
                      "optionId": "R01",
                      "evidence": "EXPLICIT_SELECTION"
                    }
                  ]
                }
                """);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StyleCoreCandidateExtractor.extract(
                                observations,
                                loadMapping()
                        )
                );

        assertEquals(
                "INVALID_OBSERVATION_DEFINITION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsObservationWhenMappingDefinitionDiffers()
            throws Exception {

        JsonNode observations = mapper.readTree("""
            {
              "DIRECT_FASHION_LOOK_PREFERENCE": [
                {
                  "definitionId": "%s",
                  "questionId": "Q02",
                  "optionId": "R01",
                  "evidence": "EXPLICIT_SELECTION"
                }
              ]
            }
            """.formatted(DEFINITION_ID));

        JsonNode mapping = loadMapping();

        // 관측 데이터와 다른 테스트 정의를 지정한다.
        ((tools.jackson.databind.node.ObjectNode) mapping)
                .put(
                        "sourceDefinitionId",
                        "11111111-1111-4111-8111-111111111111"
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StyleCoreCandidateExtractor.extract(
                                observations,
                                mapping
                        )
                );

        assertEquals(
                "INVALID_OBSERVATION_DEFINITION",
                exception.getMessage()
        );
    }

}
