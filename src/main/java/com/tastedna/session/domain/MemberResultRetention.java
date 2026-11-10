package com.tastedna.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

// 응답은 원본 세션에 두고, 명시적 보관 시점의 판별 결과와 두 가지 독립적인 보호 의사를 보존한다.
@Entity
@Table(name = "td_member_result_retention", uniqueConstraints = {
        @UniqueConstraint(name = "uq_result_retention_slot", columnNames = {"mall_member_id", "saved_slot"}),
        @UniqueConstraint(name = "uq_result_retention_current", columnNames = {"mall_member_id", "current_marker"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberResultRetention {
    public static final int MAX_SAVED_RESULTS = 5;

    @Id
    @Column(name = "session_id", columnDefinition = "CHAR(36)")
    private String sessionId;
    @Column(name = "mall_member_id", nullable = false, length = 128, updatable = false)
    private String mallMemberId;
    @Column(name = "submission_revision", nullable = false, updatable = false)
    private long submissionRevision;
    @Column(name = "rule_version", nullable = false, length = 255, updatable = false)
    private String ruleVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "json", updatable = false)
    private String resultSnapshot;
    @Column(name = "captured_at", nullable = false, updatable = false)
    private LocalDateTime capturedAt;
    @Column(name = "saved_slot")
    private Integer savedSlot;
    @Column(name = "saved_at")
    private LocalDateTime savedAt;
    @Column(name = "current_marker")
    private Integer currentMarker;
    @Column(name = "current_since")
    private LocalDateTime currentSince;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public MemberResultRetention(String memberId, TestSession session, String ruleVersion,
                                 String resultSnapshot, LocalDateTime capturedAt) {
        if (!Objects.equals(memberId, session.getMallMemberId()))
            throw new IllegalArgumentException("SESSION_OWNER_MISMATCH");
        if (!"SUBMITTED".equals(session.getStatus()) || session.getSubmittedAt() == null)
            throw new IllegalStateException("SESSION_NOT_SUBMITTED");
        if (ruleVersion == null || ruleVersion.isBlank() || ruleVersion.length() > 255
                || resultSnapshot == null || resultSnapshot.isBlank())
            throw new IllegalArgumentException("RESULT_SNAPSHOT_REQUIRED");
        this.sessionId = session.getSessionId();
        this.mallMemberId = memberId;
        this.submissionRevision = session.getRevision();
        this.ruleVersion = ruleVersion;
        this.resultSnapshot = resultSnapshot;
        this.capturedAt = Objects.requireNonNull(capturedAt);
    }

    // 슬롯 번호는 표시 순위가 아니라 동시 저장에서도 5개 한도를 지키기 위한 DB 제약용 식별자다.
    public void saveToSlot(int slot, LocalDateTime now) {
        if (slot < 1 || slot > MAX_SAVED_RESULTS) throw new IllegalArgumentException("INVALID_SAVED_SLOT");
        Objects.requireNonNull(now);
        if (savedSlot != null) return;
        savedSlot = slot;
        savedAt = now;
    }

    public void removeSaved() {
        savedSlot = null;
        savedAt = null;
    }

    public void setCurrent(LocalDateTime now) {
        Objects.requireNonNull(now);
        if (currentMarker != null) return;
        currentMarker = 1;
        currentSince = now;
    }

    public void clearCurrent() {
        currentMarker = null;
        currentSince = null;
    }

    public boolean isProtected() {
        return savedSlot != null || currentMarker != null;
    }
}
