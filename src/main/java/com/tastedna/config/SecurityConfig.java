package com.tastedna.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// 실행 프로필에 따라 TasteDNA API의 인증 및 접근 정책을 설정한다.
// 현재 로컬 개발용 인증과 그 외 환경의 기본 접근 차단 정책을 구분한다.
@Configuration
public class SecurityConfig {

    // local 프로필에서는 개발용 Basic 인증을 사용하며, 헬스 체크는 인증 없이 허용한다.
    // CSRF를 비활성화한 개발 환경 설정이므로 운영용 보안 정책으로 그대로 사용하지 않는다.
    @Bean
    @Profile("local")
    public SecurityFilterChain localSecurity(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET, "/api/v1/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> {});

        return http.build();
    }

    // 환경 변수의 개발용 비밀번호로 테스트 회원 계정을 메모리에 생성한다.
    // 실제 쇼핑몰 회원 DB나 외부 인증 시스템과 연동하는 운영용 회원 관리는 아니다.
    @Bean
    @Profile("local")
    public UserDetailsService localUsers(
            org.springframework.core.env.Environment environment) {

        String password = environment.getRequiredProperty(
                "DEV_MEMBER_PASSWORD");

        // 개발용 비밀번호를 BCrypt 방식으로 인코딩해 Spring Security에 등록한다.
        // 비밀번호 원문은 소스 코드에 저장하지 않고 DEV_MEMBER_PASSWORD 설정에서 읽는다.
        var encoder = new BCryptPasswordEncoder();
        String encodedPassword =
                "{bcrypt}" + encoder.encode(password);

        var member001 = User.withUsername("dev-member-001")
                .password(encodedPassword)
                .roles("MEMBER")
                .build();

        var member002 = User.withUsername("dev-member-002")
                .password(encodedPassword)
                .roles("MEMBER")
                .build();

        return new InMemoryUserDetailsManager(
                member001,
                member002
        );
    }

    // local 이외의 프로필에서는 헬스 체크만 허용하고 나머지 요청을 차단한다.
    // 운영용 회원 인증 및 쇼핑몰 연동 보안이 구현되기 전까지 사용하는 기본 차단 정책이다.
    @Bean
    @Profile("!local")
    public SecurityFilterChain defaultSecurity(HttpSecurity http)
            throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET, "/api/v1/health"
                        ).permitAll()
                        .anyRequest().denyAll()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
