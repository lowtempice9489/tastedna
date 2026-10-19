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

@Getter
@Entity
@Table(name = "td_session_answer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionAnswer {

    @EmbeddedId
    private SessionAnswerId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "answer_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String answerPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "presented_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String presentedPayload;

    @Column(name = "answer_revision", nullable = false)
    private long answerRevision;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

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
