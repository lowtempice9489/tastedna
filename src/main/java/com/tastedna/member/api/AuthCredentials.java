package com.tastedna.member.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

// 가입과 로그인에서 같은 정규화를 사용한다. 비밀번호는 공백 제거·대소문자 변환을 하지 않는다.
public record AuthCredentials(
        @NotBlank @Email @Size(max = 254) @Pattern(regexp = "^[\\x21-\\x7E]+$") String email,
        @NotBlank @Size(min = 12, max = 72) String password) {
    public AuthCredentials {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }
    @JsonIgnore
    @AssertTrue
    public boolean isPasswordEncodingValid() {
        // BCrypt의 72바이트 한계를 초과하는 비밀번호를 조용히 잘라 저장하지 않는다.
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.chars().noneMatch(Character::isISOControl);
    }
    @Override public String toString() { return "AuthCredentials[REDACTED]"; }
}
