package com.tastedna.scoring.api;

import com.tastedna.scoring.application.RawObservationAggregator;
import com.tastedna.scoring.application.SessionObservationService;
import com.tastedna.session.application.TestSessionService;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class RawObservationController {

    private final TestSessionService sessionService;
    private final SessionObservationService observationService;

    @GetMapping("/{sessionId}/raw-observations")
    public Map<String, List<RawObservationAggregator.RawObservation>>
    getRawObservations(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        String memberId = authentication.getName();

        // 기존 세션 서비스에서 접근 권한 확인
        sessionService.get(memberId, sessionId);

        try {
            return observationService.collectRaw(
                    memberId,
                    sessionId.toString()
            );

        } catch (IllegalStateException e) {

            String code = e.getMessage();

            if ("OBSERVATION_KIND_NOT_SUPPORTED".equals(code)
                    || "SCORING_RULE_NOT_FOUND".equals(code)
                    || "OBSERVATION_RULE_NOT_FOUND".equals(code)) {

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
