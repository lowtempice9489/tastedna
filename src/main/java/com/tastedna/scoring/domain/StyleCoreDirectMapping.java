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

// Q02 직접 선호 선택지와 RESULT 후보 사이의 DB 매핑이다.
// 직접 선택 근거만 표현하며 최종 종합 RESULT를 확정하지 않는다.
@Getter
@Entity
@Table(name = "td_style_core_direct_mapping")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StyleCoreDirectMapping {

    @EmbeddedId
    private MappingId id;

    @Column(name = "candidate_id", length = 40, nullable = false)
    private String candidateId;

    // Definition·질문·선택지로 구성된 기존 DB 복합 기본키.
    @Getter
    @Embeddable
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class MappingId implements Serializable {

        @Column(name = "source_definition_id", length = 36)
        private String sourceDefinitionId;

        @Column(name = "question_id", length = 20)
        private String questionId;

        @Column(name = "option_id", length = 40)
        private String optionId;
    }
}
