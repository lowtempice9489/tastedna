package com.tastedna.session.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

// 질문 답변 저장 요청의 입력 계약을 정의한다.
// expectedRevision은 현재 세션 버전과 비교해 오래된 수정 요청을 차단하는 데 사용한다.
public record SaveAnswerRequest(

        @NotBlank
        String questionId,

        @NotNull
        @Min(0)
        Long expectedRevision,

        @NotNull
        JsonNode answerPayload

) {

        // @NotNull만으로 걸러지지 않을 수 있는 JSON null 노드도 유효하지 않은 답변으로 처리한다.
        // 검증용 메서드는 JSON 요청 필드로 취급되지 않도록 직렬화 대상에서 제외한다.
        @JsonIgnore
        @AssertTrue(message = "answerPayload must not be JSON null")
        public boolean isAnswerPayloadValid() {
                return answerPayload != null
                        && !answerPayload.isNull();
        }
}
