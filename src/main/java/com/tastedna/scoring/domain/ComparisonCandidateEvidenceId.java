package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 테스트 정의·질문·비교 항목·속성값·RESULT 후보를 식별한다.
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ComparisonCandidateEvidenceId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "definition_id", columnDefinition = "CHAR(36)")
    private String definitionId;

    @Column(name = "question_id", length = 40)
    private String questionId;

    @Column(name = "comparison_id", length = 40)
    private String comparisonId;

    @Column(name = "attribute_key", length = 80)
    private String attributeKey;

    @Column(name = "attribute_value", length = 120)
    private String attributeValue;

    @Column(name = "candidate_id", length = 40)
    private String candidateId;
}
