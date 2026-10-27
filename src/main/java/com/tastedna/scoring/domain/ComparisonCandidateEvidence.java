package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Q08에서 검증된 비교 속성값과 RESULT 후보의 관계를 보관한다.
// DB 상태가 VERIFIED인 SUPPORT만 후보 지지 근거로 사용할 수 있다.
@Getter
@Entity
@Table(name = "td_comparison_candidate_evidence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComparisonCandidateEvidence {

    @EmbeddedId
    private ComparisonCandidateEvidenceId id;

    @Column(name = "evidence_relation", nullable = false, length = 24)
    private String evidenceRelation;

    @Column(name = "verification_status", nullable = false, length = 24)
    private String verificationStatus;

    // 후보 지지 근거로 사용할 수 있는 관계인지 확인한다.
    // 실제 후보 간 우열이 검증됐다는 의미는 아니다.
    public boolean isVerifiedSupport() {
        return "VERIFIED".equals(verificationStatus)
                && "SUPPORT".equals(evidenceRelation);
    }
}
