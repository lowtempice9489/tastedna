package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 테스트 정의·질문·비교 항목·선택지의 복합 식별자다.
// 예: Simple / Q08 / C01 / A
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ComparisonOptionAttributeId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "definition_id", columnDefinition = "CHAR(36)")
    private String definitionId;

    @Column(name = "question_id", length = 40)
    private String questionId;

    @Column(name = "comparison_id", length = 40)
    private String comparisonId;

    @Column(name = "option_id", length = 40)
    private String optionId;
}
