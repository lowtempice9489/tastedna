package com.tastedna.scoring;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OutfitMetadataStructureTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    // 개발용 질문 및 RESULT 메타데이터 파일을 읽는다.
    // 실제 질문 구조와 판별용 설정이 일치하는지 검사할 때 사용한다.
    private JsonNode load(String directory, String filename)
            throws Exception {
        return mapper.readTree(
                Files.readString(Path.of(
                        "dev", "fixtures", directory, filename
                ))
        );
    }

    @Test
    void singleChoiceMetadataMatchesActualQuestions()
            throws Exception {

        JsonNode metadata = load(
                "result",
                "simple_q05_q10_outfit_metadata_v0.1.json"
        );

        for (String questionId : new String[]{"Q05", "Q10"}) {

            JsonNode question = load(
                    "simple",
                    "simple_" + questionId.toLowerCase()
                            + "_question_payload.json"
            );

            JsonNode config = metadata
                    .get("questions")
                    .get(questionId);

            assertEquals("SINGLE_CHOICE",
                    question.get("kind").stringValue());

            assertEquals("SINGLE_CHOICE",
                    config.get("kind").stringValue());

            Set<String> actualIds = new HashSet<>();
            Set<String> metadataIds = new HashSet<>();

            for (JsonNode option : question.get("options")) {
                assertTrue(actualIds.add(
                        option.get("id").stringValue()
                ));
            }

            for (JsonNode option : config.get("options")) {
                assertTrue(metadataIds.add(
                        option.get("optionId").stringValue()
                ));

                assertTrue(
                        option.get("visualAssetId").isNull()
                );

                assertTrue(
                        option.get("candidateEvidenceMappings")
                                .isEmpty()
                );
            }

            assertEquals(6, actualIds.size());
            assertEquals(actualIds, metadataIds);
        }
    }

    @Test
    void comparisonMetadataMatchesActualQuestions()
            throws Exception {

        checkComparison(
                "Q08",
                "simple_q08_comparison_metadata_v0.1.json"
        );

        checkComparison(
                "Q09",
                "simple_q09_combination_metadata_v0.1.json"
        );
    }

    // 비교형 문항의 실제 비교쌍과 메타데이터를 대조한다.
    // 선택지 누락이나 검증되지 않은 RESULT 연결을 조기에 발견한다.
    private void checkComparison(
            String questionId,
            String metadataFilename
    ) throws Exception {

        JsonNode question = load(
                "simple",
                "simple_" + questionId.toLowerCase()
                        + "_question_payload.json"
        );

        JsonNode metadata = load(
                "result",
                metadataFilename
        );

        assertEquals("COMPARISON",
                question.get("kind").stringValue());

        assertEquals("COMPARISON",
                metadata.get("kind").stringValue());

        JsonNode actualComparisons =
                question.get("comparisons");

        JsonNode configuredComparisons =
                metadata.get("comparisons");

        assertEquals(3, actualComparisons.size());
        assertEquals(
                actualComparisons.size(),
                configuredComparisons.size()
        );

        for (int i = 0; i < actualComparisons.size(); i++) {

            JsonNode actual = actualComparisons.get(i);
            JsonNode configured = configuredComparisons.get(i);

            assertEquals(
                    actual.get("id").stringValue(),
                    configured.get("comparisonId").stringValue()
            );

            Set<String> actualIds = new HashSet<>();
            Set<String> configuredIds = new HashSet<>();

            for (JsonNode option : actual.get("options")) {
                assertTrue(actualIds.add(
                        option.get("id").stringValue()
                ));
            }

            for (JsonNode option : configured.get("options")) {
                assertTrue(configuredIds.add(
                        option.get("optionId").stringValue()
                ));

                assertTrue(
                        option.get("visualAssetId").isNull()
                );
            }

            assertEquals(Set.of("A", "B"), actualIds);
            assertEquals(actualIds, configuredIds);

            assertTrue(
                    configured.get("candidateEvidenceMappings")
                            .isEmpty()
            );
        }
    }
}
