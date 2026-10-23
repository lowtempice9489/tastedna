package com.tastedna.session;

import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import com.tastedna.session.application.multi.MultiChoiceOptionExtractor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// 실제 Simple Q01 질문 Fixture와 복수 선택 답변 검증 규칙의 일치 여부를 확인한다.
// 질문 선택지 구성이나 최대 선택 개수가 변경될 때 의도치 않은 계약 변경을 발견하기 위한 테스트다.
class MultiChoiceQ01FixtureTest {

    // Q01의 선택지가 15개이고 최대 5개까지 선택 가능한지 검증한다.
    // 정상적인 5개 선택은 허용하고 6개 선택은 거부해야 한다.
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
