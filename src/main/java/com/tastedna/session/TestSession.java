
package com.tastedna.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "td_test_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestSession {

    @Id
    @Column(name = "session_id", columnDefinition = "CHAR(36)")
    private String sessionId;

    @Column(name = "mall_member_id", nullable = false, length = 128)
    private String mallMemberId;

    @Column(name = "definition_id",
            nullable = false, columnDefinition = "CHAR(36)")
    private String definitionId;

    @Column(name = "apparel_scope", nullable = false, length = 6)
    private String apparelScope;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "revision", nullable = false)
    private long revision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_order",
            columnDefinition = "json", nullable = false)
    private String questionOrder;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "lease_until", nullable = false)
    private LocalDateTime leaseUntil;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "result_id", columnDefinition = "CHAR(36)")
    private String resultId;

    public TestSession(
            String sessionId,
            String mallMemberId,
            String definitionId,
            String apparelScope,
            String questionOrder,
            LocalDateTime now,
            LocalDateTime leaseUntil) {

        this.sessionId = sessionId;
        this.mallMemberId = mallMemberId;
        this.definitionId = definitionId;
        this.apparelScope = apparelScope;
        this.status = "IN_PROGRESS";
        this.revision = 0L;
        this.questionOrder = questionOrder;
        this.lastSeenAt = now;
        this.leaseUntil = leaseUntil;
        this.startedAt = now;
    }

    public void resume(
            LocalDateTime now,
            LocalDateTime newLeaseUntil) {

        if (!"IN_PROGRESS".equals(status)) {
            throw new IllegalStateException(
                    "SESSION_NOT_RESUMABLE"
            );
        }

        if (!newLeaseUntil.isAfter(now)) {
            throw new IllegalArgumentException(
                    "INVALID_LEASE_UNTIL"
            );
        }

        this.lastSeenAt = now;
        this.leaseUntil = newLeaseUntil;
    }
}
