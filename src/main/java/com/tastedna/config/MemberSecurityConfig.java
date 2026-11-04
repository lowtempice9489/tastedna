package com.tastedna.config;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.session.*;
import java.util.List;
import com.tastedna.member.persistence.FormMemberRepository;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.*;

// 실제 회원 검증은 별도 명시적 프로필로 연다. 기본 차단과 local 개발 Basic 인증을 혼합하지 않는다.
@Configuration
@Profile("member-auth")
public class MemberSecurityConfig {
    public MemberSecurityConfig(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("local")))
            throw new IllegalStateException("member-auth and local profiles must not be combined");
    }
    @Bean public PasswordEncoder memberPasswordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public UserDetailsService memberUsers(FormMemberRepository members) {
        return email -> {
            var member = members.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("INVALID_CREDENTIALS"));
            return User.withUsername(member.getMemberId()).password(member.getPasswordHash())
                    .roles(member.getRole()).disabled(!"ACTIVE".equals(member.getStatus())).build();
        };
    }
    @Bean public AuthenticationManager memberAuthenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        // DAO 제공자가 비밀번호 비교·미등록 이메일의 타이밍 완화·인증 후 자격 증명 제거를 담당한다.
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean public HttpSessionSecurityContextRepository memberSecurityContexts() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean public HttpSessionCsrfTokenRepository memberCsrfTokens() { return new HttpSessionCsrfTokenRepository(); }
    @Bean public SessionAuthenticationStrategy memberSessionStrategy(HttpSessionCsrfTokenRepository tokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(tokens)));
    }
    @Bean public SecurityFilterChain memberSecurity(HttpSecurity http,
            HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository tokens,
            FormMemberRepository members) throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> {
            res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"로그인이 필요합니다.\"}");
        };
        http.securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
                // 인증 복원 후 CSRF 검사 전에 폐기하여 잘못된 CSRF 요청으로도 비활성 세션을 유지할 수 없게 한다.
                .addFilterBefore(new MemberStatusFilter(members, entryPoint), CsrfFilter.class)
                .csrf(csrf -> csrf.csrfTokenRepository(tokens))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me", "/api/v1/sessions/active",
                                "/api/v1/sessions/{sessionId}", "/api/v1/sessions/{sessionId}/questions",
                                "/api/v1/sessions/{sessionId}/questions/{questionId}",
                                "/api/v1/sessions/{sessionId}/answers", "/api/v1/sessions/{sessionId}/preference-summary")
                        .hasRole("MEMBER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/sessions", "/api/v1/sessions/simple-image",
                                "/api/v1/sessions/{sessionId}/resume", "/api/v1/sessions/{sessionId}/answers",
                                "/api/v1/sessions/{sessionId}/submit").hasRole("MEMBER")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                            String code = ex instanceof org.springframework.security.web.csrf.CsrfException
                                    ? "CSRF_INVALID" : "ACCESS_DENIED";
                            res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"요청이 허용되지 않습니다.\"}");
                        }))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("FORMSESSION")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
        return http.build();
    }
}
