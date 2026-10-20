package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "td_option_scoring_rule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionScoringRule {

    @EmbeddedId
    private OptionScoringRuleId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "scoring_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String scoringPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "observation_payload",
            columnDefinition = "json"
    )
    private String observationPayload;
}
