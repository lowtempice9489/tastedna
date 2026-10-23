package com.tastedna.scoring;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StyleCoreEvidenceMappingTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    @Test
    void mappingMatchesActualQ02Options() throws Exception {

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
