package com.tastedna.session.api.dto;

import jakarta.validation.constraints.Min;

// 제출 시 클라이언트가 마지막으로 확인한 세션 Revision을 전달한다.
// 서버 Revision과 다르면 오래된 화면의 제출 요청을 거부한다.
public record SubmitSimpleRequest(
        @Min(0) long expectedRevision
) {
}