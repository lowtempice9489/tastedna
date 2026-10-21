package com.tastedna.session;

import com.tastedna.session.application.SingleChoiceAnswerValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class SingleChoiceAnswerValidatorTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    private static final String QUESTION_PAYLOAD = """
            {
              "kind": "SINGLE_CHOICE",
              "prompt": "개발용 질문",
              "options": [
                {"id": "A", "label": "선택지 A"},
                {"id": "B", "label": "선택지 B"},
                {"id": "C", "label": "선택지 C"}
              ]
            }
            """;

    @Test
    void acceptsValidSelection() {
        assertDoesNotThrow(() ->
                SingleChoiceAnswerValidator.validate(
                        QUESTION_PAYLOAD,
                        "B",
                        mapper
                )
        );
    }

    @Test
    void rejectsUnknownSelection() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceAnswerValidator.validate(
                        QUESTION_PAYLOAD,
                        "D",
                        mapper
                )
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsInvalidQuestionDefinition() {
        String invalidPayload = """
                {
                  "kind": "SINGLE_CHOICE",
                  "options": [
                    {"id": "A"},
                    {"id": "A"}
                  ]
                }
                """;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceAnswerValidator.validate(
                        invalidPayload,
                        "A",
                        mapper
                )
        );

        assertEquals(
                "INVALID_OPTION_DEFINITION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNullSelection() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceAnswerValidator.validate(
                        QUESTION_PAYLOAD,
                        (String) null,
                        mapper
                )
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getMessage()
        );
    }

    @Test
    void acceptsValidJsonAnswerPayload() {
        JsonNode answerPayload = mapper.readTree("""
            {"selectedOptionId":"B"}
            """);

        assertDoesNotThrow(() ->
                SingleChoiceAnswerValidator.validate(
                        QUESTION_PAYLOAD,
                        answerPayload,
                        mapper
                )
        );
    }

    @Test
    void rejectsUnknownOptionInJsonAnswerPayload() {
        JsonNode answerPayload = mapper.readTree("""
            {"selectedOptionId":"D"}
            """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceAnswerValidator.validate(
                        QUESTION_PAYLOAD,
                        answerPayload,
                        mapper
                )
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsAdditionalFieldsInJsonAnswerPayload() {
        JsonNode answerPayload = mapper.readTree("""
            {
              "selectedOptionId":"B",
              "score":100
            }
            """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceAnswerValidator.validate(
                        QUESTION_PAYLOAD,
                        answerPayload,
                        mapper
                )
        );

        assertEquals(
                "INVALID_ANSWER_PAYLOAD",
                exception.getMessage()
        );
    }

}
