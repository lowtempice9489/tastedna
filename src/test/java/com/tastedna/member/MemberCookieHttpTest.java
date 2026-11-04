package com.tastedna.member;

import com.tastedna.config.MemberSecurityConfig;
import com.tastedna.member.api.MemberAuthController;
import com.tastedna.member.application.*;
import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.session.api.SessionExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

// DB 없이 실제 내장 Servlet 서버의 Set-Cookie 속성을 검증한다. MySQL 검증은 아니다.
@SpringBootTest(classes = MemberCookieHttpTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")
@ActiveProfiles("member-auth")
class MemberCookieHttpTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({MemberSecurityConfig.class, MemberAuthController.class, MemberAuthService.class,
            AuthAttemptLimiter.class, SessionExceptionHandler.class})
    static class App {}
    @Value("${local.server.port}") int port;
    @MockitoBean FormMemberRepository members;

    @Test void realSessionCookieHasSecureHttpOnlySameSiteAndCsrfIsNotCached() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/csrf"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
            assertTrue(cookie.startsWith("FORMSESSION="));
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("Secure"));
            assertTrue(cookie.contains("SameSite=Lax"));
            assertTrue(cookie.contains("Path=/"));
            assertFalse(cookie.contains("Domain="));
            assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
            assertTrue(response.body().contains("X-CSRF-TOKEN"));
        }
    }
}
