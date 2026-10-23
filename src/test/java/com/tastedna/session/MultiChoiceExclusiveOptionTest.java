package com.tastedna.session;

import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MultiChoiceExclusiveOptionTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private final String questionPayload = loadQuestion();

    private String loadQuestion() {
        try {
            return Files.readString(
                    Path.of(
                            "dev", "fixtures", "simple",
                            "simple_q07_question_payload.json"
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Q7_TEST_FIXTURE_NOT_FOUND",
                    e
            );
        }
    }

    @Test
    void acceptsNormalSelectionsAndNoneAlone() throws Exception {
        assertDoesNotThrow(() ->
                MultiChoiceAnswerValidator.validate(
                        questionPayload,
                        mapper.readTree(
                                "{\"selectedOptionIds\":[\"A\",\"B\"]}"
                        ),
                        mapper
                )
        );

        assertDoesNotThrow(() ->
                MultiChoiceAnswerValidator.validate(
                        questionPayload,
                        mapper.readTree(
                                "{\"selectedOptionIds\":[\"NONE\"]}"
                        ),
                        mapper
                )
        );
    }

    @Test
    void rejectsNoneCombinedWithAnotherOption() throws Exception {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> MultiChoiceAnswerValidator.validate(
                                questionPayload,
                                mapper.readTree(
                                        "{\"selectedOptionIds\":[\"A\",\"NONE\"]}"
                                ),
                                mapper
                        )
                );

        assertEquals(
                "EXCLUSIVE_OPTION_CONFLICT",
                exception.getMessage()
        );
    }
}
