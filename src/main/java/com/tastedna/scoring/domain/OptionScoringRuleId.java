package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OptionScoringRuleId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "definition_id", columnDefinition = "CHAR(36)")
    private String definitionId;

    @Column(name = "question_id", length = 40)
    private String questionId;

    @Column(name = "option_id", length = 40)
    private String optionId;
}
