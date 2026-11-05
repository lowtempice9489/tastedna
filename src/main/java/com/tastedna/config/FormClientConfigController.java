package com.tastedna.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 인증 실패를 개발 모드로 오인하지 않도록 서버가 공개 가능한 인증 방식만 전달한다.
@RestController
@Profile("local | member-auth")
public class FormClientConfigController {
    private final Environment environment;

    public FormClientConfigController(Environment environment) { this.environment = environment; }

    @GetMapping("/api/v1/form/config")
    public Map<String, String> config() {
        return Map.of("authMode", environment.acceptsProfiles(Profiles.of("member-auth")) ? "member" : "local");
    }
}
