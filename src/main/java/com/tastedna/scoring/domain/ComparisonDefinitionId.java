package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 테스트 정의, 질문, 비교 항목을 함께 식별한다.
// 예: Simple 정의의 Q08 / C01
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ComparisonDefinitionId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "definition_id", columnDefinition = "CHAR(36)")
    private String definitionId;

    @Column(name = "question_id", length = 40)
    private String questionId;

    @Column(name = "comparison_id", length = 40)
    private String comparisonId;
}
