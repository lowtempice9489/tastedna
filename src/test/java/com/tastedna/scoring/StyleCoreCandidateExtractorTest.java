package com.tastedna.scoring;

import com.tastedna.scoring.application.StyleCoreCandidateExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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
