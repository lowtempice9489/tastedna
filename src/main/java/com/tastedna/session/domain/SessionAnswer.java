package com.tastedna.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// 세션의 질문별 답변과 당시 제시된 질문 내용을 DB에 저장하는 JPA 엔티티다.
// 동일 질문의 답변 수정 시 기존 레코드를 갱신하며, 수정 이력을 별도로 누적하지 않는다.
@Getter
@Entity
@Table(name = "td_session_answer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionAnswer {

    @EmbeddedId
    private SessionAnswerId id;

    // 사용자가 제출한 답변 내용을 JSON으로 보관한다.
    // 질문 유형에 따라 단일 선택, 복수 선택, 비교형 답변 등 서로 다른 구조를 가진다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "answer_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String answerPayload;

    // 답변 저장 시점에 사용된 질문 정의 JSON을 함께 보관한다.
    // Raw 관측 생성 시 질문 유형과 비교쌍 구성 등을 해석하는 데 사용한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "presented_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String presentedPayload;

    // 이 답변이 마지막으로 저장·수정된 세션 Revision을 기록한다.
    // 관측 결과가 어떤 답변 버전에서 생성됐는지 추적할 때 사용한다.
    @Column(name = "answer_revision", nullable = false)
    private long answerRevision;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    // 세션 ID와 질문 ID를 복합 키로 사용해 새 답변 레코드를 생성한다.
    // 답변 내용, 제시된 질문 내용, Revision 및 답변 시각을 함께 저장한다.
    public SessionAnswer(
            String sessionId,
            String questionId,
            String answerPayload,
            String presentedPayload,
            long answerRevision,
            LocalDateTime answeredAt) {

        this.id = new SessionAnswerId(sessionId, questionId);
        this.answerPayload = answerPayload;
        this.presentedPayload = presentedPayload;
        this.answerRevision = answerRevision;
        this.answeredAt = answeredAt;
    }

    // 이미 존재하는 질문 답변을 새로운 내용과 Revision으로 갱신한다.
    // 이전 답변은 이 레코드에 보존되지 않으므로 별도의 변경 이력으로 간주해서는 안 된다.
    public void update(
            String answerPayload,
            String presentedPayload,
            long answerRevision,
            LocalDateTime answeredAt) {

        this.answerPayload = answerPayload;
        this.presentedPayload = presentedPayload;
        this.answerRevision = answerRevision;
        this.answeredAt = answeredAt;
    }
}
