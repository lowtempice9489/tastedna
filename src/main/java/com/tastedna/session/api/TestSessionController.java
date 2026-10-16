
package com.tastedna.session.api;

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
}
