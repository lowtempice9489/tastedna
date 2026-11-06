package com.tastedna.session.api.dto;

import java.time.Instant;
import java.util.UUID;

// 저장된 시각만 공개하며 제출 완료를 최종 RESULT 확정으로 표현하지 않는다.
public record SessionHistoryResponse(UUID sessionId, Mode mode, ApparelScope apparelScope,
                                     String status, Instant startedAt, Instant submittedAt) {}
