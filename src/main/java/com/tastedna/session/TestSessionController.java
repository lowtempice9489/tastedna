
package com.tastedna.session;

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
    public ResponseEntity<SessionDtos.SessionResponse> start(
            @Valid @RequestBody SessionDtos.StartRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.start(authentication.getName(), request)
        );
    }

    @GetMapping("/active")
    public ResponseEntity<SessionDtos.SessionResponse> active(
            Authentication authentication) {

        return service.findActive(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<SessionDtos.SessionResponse> resume(
            @PathVariable UUID sessionId,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.resume(authentication.getName(), sessionId)
        );
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDtos.SessionResponse> get(
            @PathVariable UUID sessionId,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.get(authentication.getName(), sessionId)
        );
    }
}
