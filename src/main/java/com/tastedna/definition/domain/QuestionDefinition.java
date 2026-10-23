package com.tastedna.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 특정 테스트 정의에 속한 질문의 구성과 표시 순서를 저장하는 JPA 엔티티다.
// 질문 ID는 테스트 정의 ID와 함께 복합 키로 관리한다.
@Getter
@Entity
@Table(name = "td_question_definition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionDefinition {

    @EmbeddedId
    private QuestionDefinitionId id;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    // 질문 유형, 표시 내용, 선택지 또는 비교쌍 등의 정의를 JSON으로 보관한다.
    // 답변 저장 시 질문 유형별 Validator가 이 데이터를 기준으로 입력을 검증한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "question_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String questionPayload;

    // 질문의 의존 조건을 표현하기 위해 마련된 JSON 필드다.
    // 현재 TestSessionService의 질문 조회·답변 저장에서는 의존 조건을 해석하지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "dependency_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String dependencyPayload;

    @Column(name = "required", nullable = false)
    private boolean required;
}
