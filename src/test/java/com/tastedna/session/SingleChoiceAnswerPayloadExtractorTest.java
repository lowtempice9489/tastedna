package com.tastedna.session;

import com.tastedna.session.application.SingleChoiceAnswerPayloadExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class SingleChoiceAnswerPayloadExtractorTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    @Test
    void extractsSelectedOptionId() {
        JsonNode payload = parse("""
                {"selectedOptionId":"A"}
                """);

        assertEquals(
                "A",
                SingleChoiceAnswerPayloadExtractor.extract(payload)
        );
    }

    @Test
    void rejectsMissingSelectedOptionId() {
        assertInvalidPayload("""
                {"otherField":"A"}
                """);
    }

    @Test
    void rejectsNullSelectedOptionId() {
        assertInvalidPayload("""
                {"selectedOptionId":null}
                """);
    }

    @Test
    void rejectsBlankSelectedOptionId() {
        assertInvalidPayload("""
                {"selectedOptionId":"   "}
                """);
    }

    @Test
    void rejectsArraySelectedOptionId() {
        assertInvalidPayload("""
                {"selectedOptionId":["A"]}
                """);
    }

    @Test
    void rejectsAdditionalFields() {
        assertInvalidPayload("""
                {
                  "selectedOptionId":"A",
                  "score":100
                }
                """);
    }

    @Test
    void rejectsArrayAnswerPayload() {
        assertInvalidPayload("""
                ["A"]
                """);
    }

    @Test
    void rejectsNullAnswerPayload() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceAnswerPayloadExtractor.extract(null)
        );

        assertEquals(
                "INVALID_ANSWER_PAYLOAD",
                exception.getMessage()
        );
    }

    private JsonNode parse(String json) {
        return mapper.readTree(json);
    }

    private void assertInvalidPayload(String json) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceAnswerPayloadExtractor.extract(
                        parse(json)
                )
        );

        assertEquals(
                "INVALID_ANSWER_PAYLOAD",
                exception.getMessage()
        );
    }
}
