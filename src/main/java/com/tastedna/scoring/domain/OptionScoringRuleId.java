package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 테스트 정의·질문·선택지 ID를 조합해 관측 규칙을 고유하게 식별하는 복합 키 클래스다.
// OptionScoringRule 엔티티의 @EmbeddedId로 사용되며, 규칙 데이터 자체는 저장하지 않는다.
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OptionScoringRuleId implements Serializable {

    // Serializable 객체의 직렬화 버전을 구분하기 위한 값이다.
    // RESULT 점수나 테스트 정의 버전을 나타내는 값은 아니다.
    private static final long serialVersionUID = 1L;

    // 어떤 테스트 정의에 속한 규칙인지 식별한다.
    // 같은 질문·선택지 ID라도 테스트 정의가 다르면 별도 규칙으로 구분한다.
    @Column(name = "definition_id", columnDefinition = "CHAR(36)")
    private String definitionId;

    // 테스트 정의 안에서 해당 규칙이 속한 질문을 식별한다.
    // 질문마다 선택지 ID가 반복될 수 있으므로 복합 키에 포함한다.
    @Column(name = "question_id", length = 40)
    private String questionId;

    // 질문 안에서 해당 규칙이 적용되는 선택지를 식별한다.
    // definitionId 및 questionId와 함께 하나의 규칙을 지정한다.
    @Column(name = "option_id", length = 40)
    private String optionId;
}
