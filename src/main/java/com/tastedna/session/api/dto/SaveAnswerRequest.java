package com.tastedna.session.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record SaveAnswerRequest(

        @NotBlank
        String questionId,

        @NotNull
        @Min(0)
        Long expectedRevision,

        @NotNull
        JsonNode answerPayload

) {

        @com.fasterxml.jackson.annotation.JsonIgnore
        @jakarta.validation.constraints.AssertTrue(
                message = "answerPayload must not be JSON null"
        )
        public boolean isAnswerPayloadValid() {
                return answerPayload != null
                        && !answerPayload.isNull();
        }

}
