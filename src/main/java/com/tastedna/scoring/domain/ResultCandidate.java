package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// RESULT 후보의 DB 정의를 읽기 위한 엔티티.
// 후보 등록 여부와 최종 판별 완료 여부는 구분한다.
@Getter
@Entity
@Table(name = "td_result_candidate")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResultCandidate {

    @Id
    @Column(
            name = "candidate_id",
            length = 40,
            nullable = false
    )
    private String candidateId;

    @Column(
            name = "candidate_name",
            length = 120,
            nullable = false
    )
    private String candidateName;

    @Column(
            name = "candidate_group",
            length = 40,
            nullable = false
    )
    private String candidateGroup;

    @Column(
            name = "verification_status",
            length = 24,
            nullable = false
    )
    private String verificationStatus;

    // Style Core 후보인지 확인한다.
    public boolean isStyleCore() {
        return "STYLE_CORE".equals(candidateGroup);
    }

    // 검증 상태만 확인하며, 판별 알고리즘의 완료를 의미하지 않는다.
    public boolean isVerified() {
        return "VERIFIED".equals(verificationStatus);
    }
}
