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
}
