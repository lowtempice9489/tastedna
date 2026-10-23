package com.tastedna.session;

import com.tastedna.session.domain.TestSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

// DB 없이 세션 엔티티의 재개 조건과 Revision 변경 규칙을 검증한다.
// 상태 변경 실패 시 기존 세션 정보가 유지되는지도 확인한다.
class TestSessionTest {

    private TestSession newSession() {

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 10, 16, 10, 0);

        return new TestSession(
                "11111111-1111-4111-8111-111111111111",
                "dev-member-001",
                "22222222-2222-4222-8222-222222222222",
                "MENS",
                "[\"DEV_Q01\"]",
                startedAt,
                startedAt.plusMinutes(2)
        );
    }

    @Test
    void resumeUpdatesActivityAndLease() {

        TestSession session = newSession();

        LocalDateTime resumedAt =
                LocalDateTime.of(2026, 10, 16, 11, 0);

        LocalDateTime newLeaseUntil =
                resumedAt.plusMinutes(2);

        session.resume(resumedAt, newLeaseUntil);

        assertEquals(resumedAt, session.getLastSeenAt());
        assertEquals(newLeaseUntil, session.getLeaseUntil());
        assertEquals("IN_PROGRESS", session.getStatus());
        assertEquals(0L, session.getRevision());
    }

    // 새 임대 만료 시각이 재개 시각보다 늦지 않으면 재개를 거부한다.
    // 검증 실패 후 기존 접근 시각과 임대 만료 시각이 변경되지 않아야 한다.
    @Test
    void resumeRejectsInvalidLease() {

        TestSession session = newSession();

        LocalDateTime resumedAt =
                LocalDateTime.of(2026, 10, 16, 11, 0);

        LocalDateTime originalLastSeen =
                session.getLastSeenAt();

        LocalDateTime originalLease =
                session.getLeaseUntil();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> session.resume(resumedAt, resumedAt)
        );

        assertEquals(
                "INVALID_LEASE_UNTIL",
                exception.getMessage()
        );

        assertEquals(originalLastSeen, session.getLastSeenAt());
        assertEquals(originalLease, session.getLeaseUntil());
    }

    @Test
    void advanceRevisionIncreasesRevision() {

        TestSession session = newSession();

        assertEquals(0L, session.getRevision());

        session.advanceRevision(0L);

        assertEquals(1L, session.getRevision());

        session.advanceRevision(1L);

        assertEquals(2L, session.getRevision());
    }

    // 이미 변경된 세션에 이전 Revision으로 다시 접근하면 충돌을 발생시킨다.
    // 충돌 발생 후에도 Revision이 추가로 증가하지 않는지 확인한다.
    @Test
    void advanceRevisionRejectsStaleRevision() {

        TestSession session = newSession();

        session.advanceRevision(0L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> session.advanceRevision(0L)
        );

        assertEquals(
                "SESSION_REVISION_CONFLICT",
                exception.getMessage()
        );

        assertEquals(1L, session.getRevision());
    }

    @Test
    void advanceRevisionRejectsNonEditableSession() {

        TestSession session = newSession();

        org.springframework.test.util.ReflectionTestUtils.setField(
                session,
                "status",
                "SUBMITTED"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> session.advanceRevision(0L)
        );

        assertEquals(
                "SESSION_NOT_EDITABLE",
                exception.getMessage()
        );

        assertEquals(0L, session.getRevision());
    }

}
