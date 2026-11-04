package com.tastedna.member.api;

import com.tastedna.member.application.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

// 인증 검증은 서비스가 담당하고, 여기서는 Servlet 세션과 Spring Security 컨텍스트를 연결한다.
@RestController
@Profile("member-auth")
@RequestMapping("/api/v1/auth")
public class MemberAuthController {
    private final MemberAuthService service;
    private final AuthAttemptLimiter limiter;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final HttpSessionSecurityContextRepository contexts;
    private final AuthenticationManager authenticationManager;
    public MemberAuthController(MemberAuthService service, AuthAttemptLimiter limiter,
            SessionAuthenticationStrategy sessionStrategy, HttpSessionSecurityContextRepository contexts,
            AuthenticationManager authenticationManager) {
        this.service = service; this.limiter = limiter; this.sessionStrategy = sessionStrategy; this.contexts = contexts;
        this.authenticationManager = authenticationManager;
    }
    public record CsrfView(String headerName, String token) {}
    @GetMapping("/csrf") public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberAuthService.MemberView signup(@Valid @RequestBody AuthCredentials input, HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        return service.signup(input);
    }
    @PostMapping("/login")
    public MemberAuthService.MemberView login(@Valid @RequestBody AuthCredentials input,
            HttpServletRequest request, HttpServletResponse response) {
        limiter.check(request.getRemoteAddr());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        var member = service.me(authentication.getName());
        // 컨트롤러 인증은 세션 전략과 저장을 직접 호출해야 한다. 로그인 시 ID 및 CSRF 토큰을 교체한다.
        sessionStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return member;
    }
    @GetMapping("/me") public MemberAuthService.MemberView me(Authentication authentication) {
        return service.me(authentication.getName());
    }
}
