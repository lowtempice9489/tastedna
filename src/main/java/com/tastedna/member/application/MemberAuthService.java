package com.tastedna.member.application;

import com.tastedna.member.api.AuthCredentials;
import com.tastedna.member.domain.FormMember;
import com.tastedna.member.persistence.FormMemberRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// 이메일은 로그인 조회에만 쓰고, 외부에 전달하는 인증 이름은 변경되지 않는 내부 ID다.
@Service
@Profile("member-auth")
public class MemberAuthService {
    private final FormMemberRepository members;
    private final PasswordEncoder encoder;
    public MemberAuthService(FormMemberRepository members, PasswordEncoder encoder) {
        this.members = members;
        this.encoder = encoder;
    }
    public record MemberView(String memberId, String email) {}

    @Transactional
    public MemberView signup(AuthCredentials input) {
        if (members.existsByEmail(input.email())) throw duplicate();
        var member = new FormMember("form-" + UUID.randomUUID(), input.email(), encoder.encode(input.password()),
                LocalDateTime.now(ZoneOffset.UTC));
        try { members.saveAndFlush(member); }
        catch (DataIntegrityViolationException ex) {
            // 사전 중복 조회를 통과한 동시 가입도 DB UNIQUE 제약으로 차단한다.
            throw duplicate();
        }
        return view(member);
    }

    @Transactional(readOnly = true)
    public MemberView me(String memberId) {
        return members.findById(memberId).filter(m -> "ACTIVE".equals(m.getStatus())).map(MemberAuthService::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));
    }
    private static MemberView view(FormMember member) { return new MemberView(member.getMemberId(), member.getEmail()); }
    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED");
    }
}
