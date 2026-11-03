package com.tastedna.scoring.api;

import com.tastedna.scoring.application.PreferenceSummaryService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@Profile("local")
@RequestMapping("/api/v1/sessions/{sessionId}")
public class PreferenceSummaryController {
    private final PreferenceSummaryService service;
    public PreferenceSummaryController(PreferenceSummaryService service) { this.service = service; }
    @GetMapping("/answers")
    public PreferenceSummaryService.AnswerSnapshot answers(Authentication auth, @PathVariable UUID sessionId) {
        return service.snapshot(auth.getName(), sessionId);
    }
    @GetMapping("/preference-summary")
    public PreferenceSummaryService.Summary result(Authentication auth, @PathVariable UUID sessionId) {
        return service.summarize(auth.getName(), sessionId);
    }
}

