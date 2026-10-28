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

// Simple RESULT 후보에 대한 실험용 속성 근거를 DB에서 읽는다.
// 근거가 등록되었다는 사실만으로 최종 판별을 허용하지 않는다.
@Getter
@Entity
@Table(name = "td_simple_attribute_mapping")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimpleAttributeMapping {

    @EmbeddedId
    private MappingId id;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Column(
            name = "verification_status",
            length = 24,
            nullable = false
    )
    private String verificationStatus;

    // V10 테이블의 복합 기본키 구조를 그대로 사용한다.
    @Getter
    @Embeddable
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class MappingId implements Serializable {

        @Column(name = "definition_id", length = 36)
        private String definitionId;

        @Column(name = "candidate_id", length = 40)
        private String candidateId;

        @Column(name = "question_id", length = 20)
        private String questionId;

        @Column(name = "option_id", length = 40)
        private String optionId;
    }
}
