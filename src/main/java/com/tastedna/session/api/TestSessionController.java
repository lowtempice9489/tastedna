package com.tastedna.session.api;

import com.tastedna.definition.api.dto.QuestionListResponse;
import com.tastedna.definition.api.dto.QuestionResponse;
import com.tastedna.session.api.dto.SaveAnswerRequest;
import com.tastedna.session.api.dto.SubmitSimpleRequest;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.api.dto.SessionResponse;
import com.tastedna.session.api.dto.StartSessionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 테스트 세션 생성·조회·재개, 답변 저장, 질문 조회 API를 제공한다.
// 인증된 회원 정보를 서비스에 전달하며, 실제 세션 처리와 검증은 TestSessionService가 담당한다.
@RestController
@RequestMapping("/api/v1/sessions")
public class TestSessionController {

    private final TestSessionService service;

    public TestSessionController(TestSessionService service) {
        this.service = service;
    }

    // POST /api/v1/sessions — 인증된 회원의 새 테스트 세션을 생성한다.
    // 활성 테스트 정의와 중복 세션 여부는 서비스에서 검증한다.
    @PostMapping
    public ResponseEntity<SessionResponse> start(
            @Valid @RequestBody StartSessionRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.start(authentication.getName(), request)
        );
    }

    // GET /api/v1/sessions/active — 회원의 활성 세션을 조회한다.
    // 해당 세션이 없으면 오류 대신 HTTP 204 No Content를 반환한다.
    @GetMapping("/active")
    public ResponseEntity<SessionResponse> active(
            Authentication authentication) {

        return service.findActive(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // POST /{sessionId}/resume — 진행 중인 본인 세션을 재개한다.
    // 세션 소유권과 재개 가능 상태는 서비스에서 확인한다.
    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<SessionResponse> resume(
            @PathVariable UUID sessionId,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.resume(authentication.getName(), sessionId)
        );
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> get(
            @PathVariable UUID sessionId,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.get(authentication.getName(), sessionId)
        );
    }

    // POST /{sessionId}/answers — 질문 답변을 저장하거나 수정한다.
    // 질문별 유효성 및 Revision 충돌을 검증하며, 마지막 답변 저장만으로 세션을 제출하지는 않는다.
    @PostMapping("/{sessionId}/answers")
    public ResponseEntity<SessionResponse> saveAnswer(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SaveAnswerRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.saveAnswer(
                        authentication.getName(),
                        sessionId,
                        request
                )
        );
    }

    // POST /{sessionId}/submit — Simple 테스트의 응답 완료 후 제출한다.
    // 회원 소유권, 응답 완성도, Revision과 제출 가능 상태는 서비스에서 검증한다.
    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<SessionResponse> submitSimple(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitSimpleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                service.submitSimple(
                        authentication.getName(),
                        sessionId,
                        request.expectedRevision()
                )
        );
    }

    @GetMapping("/{sessionId}/questions/{questionId}")
    public QuestionResponse getQuestion(
            Authentication authentication,
            @PathVariable UUID sessionId,
            @PathVariable String questionId
    ) {
        return service.getQuestion(
                authentication.getName(),
                sessionId,
                questionId
        );
    }

    // GET /{sessionId}/questions — 해당 세션에 배정된 질문 목록을 조회한다.
    // 세션 시작 시 저장된 질문 순서를 기준으로 응답하며, 질문 데이터는 서비스에서 구성한다.
    @GetMapping("/{sessionId}/questions")
    public QuestionListResponse getQuestions(
            Authentication authentication,
            @PathVariable UUID sessionId
    ) {
        return service.getQuestions(
                authentication.getName(),
                sessionId
        );
    }

}
