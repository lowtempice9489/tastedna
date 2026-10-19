package com.tastedna.session;

import com.tastedna.session.domain.TestSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

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
