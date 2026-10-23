package com.tastedna.session.domain;

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

// 회원별 테스트 세션의 진행 상태와 답변 Revision을 관리하는 JPA 엔티티다.
// 세션 생성·재개 시 상태를 검증하며, 답변 내용 자체는 별도 엔티티에 저장한다.
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

    // 테스트 세션의 현재 진행 상태를 저장한다.
    // 현재 생성자는 IN_PROGRESS로 시작하며, 이 클래스에는 제출·완료 상태로 전환하는 메서드가 없다.
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    // 테스트 세션의 현재 진행 상태를 저장한다.
    // 현재 생성자는 IN_PROGRESS로 시작하며, 이 클래스에는 제출·완료 상태로 전환하는 메서드가 없다.
    @Column(name = "revision", nullable = false)
    private long revision;

    // 세션 생성 당시 확정된 질문 ID의 순서를 JSON으로 보관한다.
    // 질문 조회와 답변 저장 시 세션에 포함된 질문인지 확인하는 기준이 된다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_order",
            columnDefinition = "json", nullable = false)
    private String questionOrder;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    // 세션의 임대 만료 시각을 보관하며 재개 시 새로운 시각으로 갱신한다.
    // 현재 이 엔티티의 resume()은 시각 유효성을 검사하지만 기존 임대 만료 여부는 확인하지 않는다.
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

    // 새 테스트 세션을 진행 중 상태와 Revision 0으로 초기화한다.
    // 회원·테스트 정의·질문 순서·시작 시각을 함께 저장한다.
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

    // 진행 중인 세션의 마지막 접근 시각과 임대 만료 시각을 갱신한다.
    // 새 만료 시각이 현재 시각보다 늦지 않으면 재개를 거부한다.
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

    // 진행 중인 세션에서 요청한 Revision이 현재 값과 일치할 때만 버전을 증가시킨다.
    // 충돌이나 정수 범위 초과를 검사해 답변 변경 이력의 일관성을 보호한다.
    public void advanceRevision(long expectedRevision) {

        if (!"IN_PROGRESS".equals(status)) {
            throw new IllegalStateException(
                    "SESSION_NOT_EDITABLE"
            );
        }

        if (expectedRevision != revision) {
            throw new IllegalStateException(
                    "SESSION_REVISION_CONFLICT"
            );
        }

        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "SESSION_REVISION_OVERFLOW"
            );
        }

        this.revision++;
    }

}
