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

// Simple 후보와 질문 선택지 사이의 공유·양립 관계를 DB에서 읽는다.
// COMPATIBLE은 후보를 확정하거나 다른 후보를 배제하는 근거가 아니다.
@Getter
@Entity
@Table(name = "td_simple_candidate_relation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimpleCandidateRelation {

    @EmbeddedId
    private RelationId id;

    @Column(name = "relation_type", length = 32, nullable = false)
    private String relationType;

    @Column(name = "note", length = 500, nullable = false)
    private String note;

    // V11 관계 테이블의 네 컬럼 복합 기본키.
    @Getter
    @Embeddable
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class RelationId implements Serializable {

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
