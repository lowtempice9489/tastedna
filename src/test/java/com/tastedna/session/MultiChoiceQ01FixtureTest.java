
package com.tastedna.session;

import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import com.tastedna.session.application.multi.MultiChoiceOptionExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MultiChoiceQ01FixtureTest {

    @Test
    void actualQ01FixtureFollowsAnswerContract() throws Exception {

        JsonMapper mapper = JsonMapper.builder().build();

        String questionPayload = Files.readString(
                Path.of("dev", "fixtures", "simple",
                        "simple_q01_question_payload.json")
        );

        var definition = MultiChoiceOptionExtractor.extract(
                questionPayload,
                mapper
        );

        assertEquals(15, definition.optionIds().size());
        assertEquals(5, definition.maxSelections());

        var validAnswer = mapper.readTree("""
                {
                  "selectedOptionIds": ["A", "C", "F", "J", "O"]
                }
                """);

        assertDoesNotThrow(() ->
                MultiChoiceAnswerValidator.validate(
                        questionPayload,
                        validAnswer,
                        mapper
                )
        );

        var tooMany = mapper.readTree("""
                {
                  "selectedOptionIds": ["A", "B", "C", "D", "E", "F"]
                }
                """);

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> MultiChoiceAnswerValidator.validate(
                        questionPayload,
                        tooMany,
                        mapper
                )
        );

        assertEquals(
                "TOO_MANY_OPTIONS_SELECTED",
                exception.getMessage()
        );
    }
}
