package com.tastedna.session;

import com.tastedna.session.api.dto.SaveAnswerRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

// 답변 저장 요청 DTO의 기본 입력값 검증 계약을 확인한다.
// 질문 유형별 선택지 검증은 여기서 다루지 않고 별도 Validator에서 수행한다.
class SaveAnswerRequestValidationTest {

    // answerPayload가 Java null이 아닌 JSON null 노드인 경우에도 거부해야 한다.
    // JSON 파싱 결과와 Bean Validation의 null 판단이 다를 수 있어 별도로 검증한다.
    @Test
    void rejectsJsonNullAnswerPayload() {
        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {

            Validator validator = factory.getValidator();

            SaveAnswerRequest request = new SaveAnswerRequest(
                    "DEV_Q01",
                    0L,
                    JsonMapper.builder().build().readTree("null")
            );

            var violations = validator.validate(request);

            assertFalse(
                    violations.isEmpty(),
                    "JSON null answerPayload must be rejected"
            );
        }
    }

    // null이 아닌 답변 JSON은 DTO 단계의 기본 유효성 검사를 통과해야 한다.
    // 실제 선택지의 유효성은 이후 질문 유형별 검증기가 판단한다.
    @Test
    void acceptsNonNullJsonAnswerPayload() {
        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {

            Validator validator = factory.getValidator();

            SaveAnswerRequest request = new SaveAnswerRequest(
                    "DEV_Q01",
                    0L,
                    JsonMapper.builder()
                            .build()
                            .readTree("{\"selectedOptionIds\":[\"A\"]}")
            );

            var violations = validator.validate(request);

            assertTrue(
                    violations.isEmpty(),
                    "Non-null JSON answerPayload should pass DTO validation"
            );
        }
    }

}
