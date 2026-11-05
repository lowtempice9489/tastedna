package com.tastedna.scoring.api;

import com.tastedna.scoring.application.SimpleInitialCandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Profile({"local", "member-auth"})
@RequestMapping("/api/v1/sessions/{sessionId}/initial-candidates")
public class SimpleInitialCandidateController {
    private final SimpleInitialCandidateService service;
    @GetMapping
    public SimpleInitialCandidateService.Response get(Authentication authentication, @PathVariable UUID sessionId) {
        return service.find(authentication.getName(), sessionId);
    }
}
