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

@Configuration
public class SecurityConfig {

    @Bean
    @Profile("local")
    public SecurityFilterChain localSecurity(HttpSecurity http)
            throws Exception {

        http
                // 로컬 API 테스트에서만 CSRF 비활성화
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

    @Bean
    @Profile("local")
    public UserDetailsService localUsers(
            org.springframework.core.env.Environment environment) {

        String password = environment.getRequiredProperty(
                "DEV_MEMBER_PASSWORD");

        var encoder = new BCryptPasswordEncoder();

        var member = User.withUsername("dev-member-001")
                .password("{bcrypt}" + encoder.encode(password))
                .roles("MEMBER")
                .build();

        return new InMemoryUserDetailsManager(member);
    }

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
