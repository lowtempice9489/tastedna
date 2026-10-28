package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// Simple RESULT 후보의 제안된 스타일 특성을 DB에서 읽는다.
// PROPOSED 상태는 실제 사용자 취향의 최종 판별을 의미하지 않는다.
@Getter
@Entity
@Table(name = "td_simple_candidate_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimpleCandidateProfile {

    @EmbeddedId
    private ProfileId id;

    @Column(name = "profile_status", length = 24, nullable = false)
    private String profileStatus;

    @Column(name = "primary_focus", length = 80, nullable = false)
    private String primaryFocus;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    // 테스트 정의와 RESULT 후보를 함께 식별하는 복합 기본키.
    @Getter
    @Embeddable
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class ProfileId implements Serializable {

        @Column(name = "definition_id", length = 36)
        private String definitionId;

        @Column(name = "candidate_id", length = 40)
        private String candidateId;
    }
}
