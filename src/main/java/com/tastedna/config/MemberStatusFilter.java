package com.tastedna.config;

import com.tastedna.member.persistence.FormMemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 로그인 당시 상태를 신뢰하지 않고 매 요청에서 확인해 비활성 회원의 기존 세션도 폐기한다.
final class MemberStatusFilter extends OncePerRequestFilter {
    private final FormMemberRepository members;
    private final AuthenticationEntryPoint entryPoint;
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler cookies = new CookieClearingLogoutHandler("FORMSESSION");

    MemberStatusFilter(FormMemberRepository members, AuthenticationEntryPoint entryPoint) {
        this.members = members;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && members.findById(authentication.getName())
                    .filter(member -> "ACTIVE".equals(member.getStatus())).isEmpty()) {
            logout.logout(request, response, authentication);
            cookies.logout(request, response, authentication);
            entryPoint.commence(request, response,
                    new InsufficientAuthenticationException("AUTHENTICATION_REQUIRED"));
            return;
        }
        chain.doFilter(request, response);
    }
}
