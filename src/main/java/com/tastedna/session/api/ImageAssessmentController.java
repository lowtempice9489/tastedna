package com.tastedna.session.api;

import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.api.dto.SessionResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// 새 이미지 정의의 시작점. 클라이언트가 정의 ID나 채점 규칙을 지정하지 않는다.
@RestController
@Profile("local")
@RequestMapping("/api/v1/sessions")
public class ImageAssessmentController {
    private final TestSessionService service;
    public ImageAssessmentController(TestSessionService service) { this.service = service; }

    @PostMapping("/simple-image")
    public SessionResponse start(Authentication authentication) {
        return service.startImageSimple(authentication.getName());
    }
}

