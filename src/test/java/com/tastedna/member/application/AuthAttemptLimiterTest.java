package com.tastedna.member.application;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthAttemptLimiterTest {
    @Test void boundsAttemptsPerRemoteAddressAndExpiresWindow() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        var limiter = new AuthAttemptLimiter(clock);
        for (int i = 0; i < 30; i++) limiter.check("ip-a");
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> limiter.check("ip-a")).getStatusCode().value());
        assertDoesNotThrow(() -> limiter.check("ip-b"));
        when(clock.millis()).thenReturn(600_000L);
        assertDoesNotThrow(() -> limiter.check("ip-a"));
    }
}
