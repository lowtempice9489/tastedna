package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Q08 비교 항목의 속성 주제와 검증 상태를 DB에서 조회한다.
// 실제 후보 우열이나 RESULT는 이 엔티티에서 계산하지 않는다.
@Getter
@Entity
@Table(name = "td_comparison_definition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComparisonDefinition {

    @EmbeddedId
    private ComparisonDefinitionId id;

    // C01이 어떤 속성을 비교하는지 나타낸다.
    // 이미지 정의 이전에는 null일 수 있다.
    @Column(name = "comparison_focus", length = 80)
    private String comparisonFocus;

    // DRAFT, ATTRIBUTES_DEFINED, VERIFIED
    @Column(name = "evidence_status", nullable = false, length = 24)
    private String evidenceStatus;

    // DB에 VERIFIED라고 기록되어 있는지 확인한다.
    // 실제 검증 절차 자체를 수행하는 메서드는 아니다.
    public boolean isVerified() {
        return "VERIFIED".equals(evidenceStatus);
    }
}
