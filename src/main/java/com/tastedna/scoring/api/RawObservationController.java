package com.tastedna.scoring.api;

import com.tastedna.scoring.application.RawObservationAggregator;
import com.tastedna.scoring.application.SessionObservationService;
import com.tastedna.session.application.TestSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// 저장된 세션의 Raw 관측 결과를 확인하기 위한 개발 전용 API 컨트롤러.
// dev 프로필에서만 활성화되며, 쇼핑몰에 제공할 정식 RESULT API는 아니다.
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Profile("dev")
public class RawObservationController {

    private final TestSessionService sessionService;
    private final SessionObservationService observationService;

    // 인증된 회원의 세션에서 생성된 Raw 관측을 유형별로 반환한다.
    // 최종 취향 분류나 RESULT 점수가 아닌 직접 관측 데이터를 조회한다.
    @GetMapping("/{sessionId}/raw-observations")
    public Map<String, List<RawObservationAggregator.RawObservation>>
    getRawObservations(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        String memberId = authentication.getName();

        // 인증 정보에서 회원 ID를 가져와 해당 세션에 접근할 수 있는지 확인한다.
        // 다른 회원의 세션 데이터가 조회되지 않도록 기존 세션 검증 로직을 재사용한다.
        sessionService.get(memberId, sessionId);

        try {
            // 저장된 답변을 직접 해석하지 않고 관측 수집 서비스에 처리를 위임한다.
            // 컨트롤러는 결과 전달을 담당하며 관측 생성 규칙은 서비스에 유지한다.
            return observationService.collectRaw(
                    memberId,
                    sessionId.toString()
            );

        } catch (IllegalStateException e) {

            String code = e.getMessage();

            // 관측 생성에 필요한 질문 유형이나 규칙이 준비되지 않은 경우 422 오류로 변환한다.
            // 그 밖의 IllegalStateException은 원인을 숨기지 않고 상위 예외 처리로 전달한다.
            if ("OBSERVATION_KIND_NOT_SUPPORTED".equals(code)
                    || "SCORING_RULE_NOT_FOUND".equals(code)
                    || "OBSERVATION_RULE_NOT_FOUND".equals(code)
                    || "RAW_OBSERVATION_NOT_READY".equals(code)) {

                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "RAW_OBSERVATION_NOT_READY",
                        e
                );
            }

            throw e;
        }
    }
}
