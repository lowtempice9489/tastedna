package com.tastedna.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Profile("local | member-auth")
@RequestMapping("/api/v1/sessions/{sessionId}/matched-demo-products")
public class DemoProductMatchController {
    private final DemoProductMatchService service;
    @GetMapping
    public DemoProductMatchService.Response get(Authentication authentication, @PathVariable UUID sessionId) {
        return service.match(authentication.getName(), sessionId);
    }
}
