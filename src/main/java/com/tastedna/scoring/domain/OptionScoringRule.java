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

// 선택지별 점수 규칙과 직접 관측 규칙을 DB 테이블에 연결하는 JPA 엔티티다.
// 규칙 데이터를 보관하며, 이 클래스 자체에서 RESULT 점수 계산이나 판별은 수행하지 않는다.
@Getter
@Entity
@Table(name = "td_option_scoring_rule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionScoringRule {

    // 테스트 정의 ID, 질문 ID, 선택지 ID로 구성된 복합 식별자를 사용한다.
    // 동일한 선택지 ID라도 테스트 정의와 질문이 다르면 서로 다른 규칙으로 구분한다.
    @EmbeddedId
    private OptionScoringRuleId id;

    // DB의 scoring_payload JSON을 보관하는 필드다.
    // 점수 규칙을 저장하기 위한 구조이며, 현재 RESULT 가중치나 계산 방식이 확정됐다는 뜻은 아니다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "scoring_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String scoringPayload;

    // 선택지와 연결된 직접 관측 정보를 observation_payload JSON으로 보관한다.
    // DirectObservationCollector가 이 값을 읽어 Raw 관측 생성에 사용한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "observation_payload",
            columnDefinition = "json"
    )
    private String observationPayload;
}
