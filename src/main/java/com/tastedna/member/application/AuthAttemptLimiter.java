package com.tastedna.member.application;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

// 단일 서버의 기본 완화책: 원격 IP당 가입/로그인 합계 10분에 30회. 프록시 헤더를 직접 신뢰하지 않는다.
@Component
@Profile("member-auth")
public class AuthAttemptLimiter {
    private record Window(long until, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AuthAttemptLimiter() { this(Clock.systemUTC()); }
    AuthAttemptLimiter(Clock clock) { this.clock = clock; }
    public synchronized void check(String remoteAddress) {
        long now = clock.millis();
        windows.values().removeIf(window -> window.until() <= now);
        Window window = windows.get(remoteAddress);
        if ((window == null && windows.size() >= 10_000) || (window != null && window.count() >= 30))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED");
        windows.put(remoteAddress, new Window(window == null ? now + 600_000 : window.until(),
                window == null ? 1 : window.count() + 1));
    }
}
