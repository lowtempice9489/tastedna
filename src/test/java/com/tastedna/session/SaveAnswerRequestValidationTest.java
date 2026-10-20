package com.tastedna.session;

import com.tastedna.session.api.dto.SaveAnswerRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class SaveAnswerRequestValidationTest {

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
