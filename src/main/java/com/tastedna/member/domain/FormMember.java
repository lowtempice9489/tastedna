package com.tastedna.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// FORM 회원은 인증 경계에만 속한다. TasteDNA 세션 테이블에 회원 FK를 강제하지 않는다.
@Entity
@Table(name = "td_form_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormMember {
    @Id @Column(name = "member_id", length = 41)
    private String memberId;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(nullable = false, length = 16)
    private String role;

    public FormMember(String memberId, String email, String passwordHash, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.status = "ACTIVE";
        this.role = "MEMBER";
    }
}
