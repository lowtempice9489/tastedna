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
