package com.tastedna.config;

import com.tastedna.session.application.MemberResultExpiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.UUID;

// 결과뿐 아니라 답변·요약·상품 경로로 만료된 자료를 우회 조회하는 것도 막는다.
@Configuration
@Profile("member-auth")
@RequiredArgsConstructor
public class MemberResultExpiryWebConfig implements WebMvcConfigurer {
    private final MemberResultExpiryService expiry;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!"GET".equals(request.getMethod()) || request.getUserPrincipal() == null) return true;
                Object attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (!(attributes instanceof Map<?, ?> variables) || !(variables.get("sessionId") instanceof String id)) return true;
                UUID sessionId;
                try { sessionId = UUID.fromString(id); }
                catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SESSION_ID"); }
                var review = expiry.review(request.getUserPrincipal().getName(), sessionId);
                if (review.status() == MemberResultExpiryService.Status.EXPIRED)
                    throw new ResponseStatusException(HttpStatus.GONE, "RESULT_EXPIRED");
                return true;
            }
        }).addPathPatterns("/api/v1/sessions/**");
    }
}
