package com.tastedna.session;

import com.tastedna.session.application.single.SingleChoiceOptionExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SingleChoiceOptionExtractorTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    @Test
    void extractsAllowedOptionIds() {
        String payload = """
                {
                  "kind": "SINGLE_CHOICE",
                  "prompt": "개발용 질문",
                  "options": [
                    {"id": "A", "label": "선택지 A"},
                    {"id": "B", "label": "선택지 B"}
                  ]
                }
                """;

        Set<String> ids =
                SingleChoiceOptionExtractor.extract(payload, mapper);

        assertEquals(Set.of("A", "B"), ids);
    }

    @Test
    void rejectsDuplicateOptionIds() {
        String payload = """
                {
                  "kind": "SINGLE_CHOICE",
                  "options": [
                    {"id": "A"},
                    {"id": "A"}
                  ]
                }
                """;

        assertInvalidDefinition(payload);
    }

    @Test
    void rejectsBlankOptionId() {
        String payload = """
                {
                  "kind": "SINGLE_CHOICE",
                  "options": [
                    {"id": "A"},
                    {"id": "   "}
                  ]
                }
                """;

        assertInvalidDefinition(payload);
    }

    @Test
    void rejectsEmptyOptions() {
        String payload = """
                {
                  "kind": "SINGLE_CHOICE",
                  "options": []
                }
                """;

        assertInvalidDefinition(payload);
    }

    @Test
    void rejectsUnsupportedQuestionKind() {
        String payload = """
                {
                  "kind": "DEV_PLACEHOLDER",
                  "options": [
                    {"id": "A"}
                  ]
                }
                """;

        assertInvalidDefinition(payload);
    }

    @Test
    void rejectsMalformedJson() {
        String payload = "{\"kind\":\"SINGLE_CHOICE\",\"options\":[";

        assertInvalidDefinition(payload);
    }

    private void assertInvalidDefinition(String payload) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceOptionExtractor.extract(
                        payload,
                        mapper
                )
        );

        assertEquals(
                "INVALID_OPTION_DEFINITION",
                exception.getMessage()
        );
    }
}
