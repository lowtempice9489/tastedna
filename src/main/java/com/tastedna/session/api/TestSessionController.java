package com.tastedna.session.api;

import com.tastedna.definition.api.dto.QuestionListResponse;
import com.tastedna.definition.api.dto.QuestionResponse;
import com.tastedna.session.api.dto.SaveAnswerRequest;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.api.dto.SessionResponse;
import com.tastedna.session.api.dto.StartSessionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class TestSessionController {

    private final TestSessionService service;

    public TestSessionController(TestSessionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> start(
            @Valid @RequestBody StartSessionRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.start(authentication.getName(), request)
        );
    }

    @GetMapping("/active")
    public ResponseEntity<SessionResponse> active(
            Authentication authentication) {

        return service.findActive(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

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
