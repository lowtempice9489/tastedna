package com.tastedna.session;

import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// 실제 Simple Q07 질문 정의를 사용해 배타적 선택지 규칙을 검증한다.
// 질문 Fixture가 변경되어도 NONE 선택의 독립성이 유지되는지 확인한다.
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

    // 일반 선택지의 복수 선택과 NONE 단독 선택은 모두 유효해야 한다.
    // NONE을 선택했다고 해서 일반적인 복수 선택 기능까지 제한해서는 안 된다.
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

    // NONE과 일반 선택지를 동시에 제출하면 배타적 선택 충돌로 처리한다.
    // 잘못된 조합에 대해 EXCLUSIVE_OPTION_CONFLICT를 반환하는지 확인한다.
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
