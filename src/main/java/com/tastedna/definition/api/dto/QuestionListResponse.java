package com.tastedna.definition.api.dto;

import java.util.List;
import java.util.UUID;

public record QuestionListResponse(
        UUID sessionId,
        List<QuestionResponse> questions
) {
    // 외부에서 전달된 질문 목록을 수정할 수 없는 목록으로 복사한다.
    // 응답 생성 후 원본 목록이 변경돼도 이 DTO의 목록 구성이 바뀌지 않도록 한다.
    public QuestionListResponse {
        questions = List.copyOf(questions);
    }
}
