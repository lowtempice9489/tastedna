package com.tastedna.session;

import com.tastedna.session.domain.MemberResultRetention;
import com.tastedna.session.domain.TestSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MemberResultRetentionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 0, 0);

    static TestSession submitted(String member) {
        var session = new TestSession(UUID.randomUUID().toString(), member,
                "33333333-3333-4333-8333-333333333333", "MENS", "[]", NOW, NOW.plusMinutes(2));
        session.advanceRevision(0);
        session.submit(NOW);
        return session;
    }

    static MemberResultRetention result(TestSession session) {
        return new MemberResultRetention(session.getMallMemberId(), session, "test-rule-v1",
                "{\"decision\":{\"status\":\"DEFERRED\",\"results\":[]}}", NOW);
    }

    @Test void captureDoesNotSaveOrSetCurrentAndPreservesSubmissionReference() {
        var session = submitted("member-a");
        var result = result(session);
        assertFalse(result.isProtected());
        assertNull(result.getSavedSlot());
        assertNull(result.getCurrentMarker());
        assertEquals(session.getSessionId(), result.getSessionId());
        assertEquals(1, result.getSubmissionRevision());
        assertEquals("test-rule-v1", result.getRuleVersion());
    }

    @Test void savedAndCurrentCanBeRemovedIndependentlyWithoutLosingSnapshot() {
        var result = result(submitted("member-a"));
        String snapshot = result.getResultSnapshot();
        result.setCurrent(NOW);
        assertNull(result.getSavedSlot());
        result.saveToSlot(5, NOW);
        result.removeSaved();
        assertTrue(result.isProtected());
        assertEquals(1, result.getCurrentMarker());
        result.saveToSlot(2, NOW.plusDays(1));
        result.clearCurrent();
        assertTrue(result.isProtected());
        assertEquals(2, result.getSavedSlot());
        result.removeSaved();
        assertFalse(result.isProtected());
        assertEquals(snapshot, result.getResultSnapshot());
    }

    @Test void repeatedActionsDoNotConsumeAnotherSlotOrChangeTimestamps() {
        var result = result(submitted("member-a"));
        result.saveToSlot(1, NOW);
        result.saveToSlot(2, NOW.plusDays(1));
        result.setCurrent(NOW);
        result.setCurrent(NOW.plusDays(1));
        assertEquals(1, result.getSavedSlot());
        assertEquals(NOW, result.getSavedAt());
        assertEquals(NOW, result.getCurrentSince());
        assertThrows(IllegalArgumentException.class, () -> result.saveToSlot(0, NOW));
        assertThrows(IllegalArgumentException.class, () -> result.saveToSlot(6, NOW));
    }

    @Test void onlyOwnedSubmittedSessionsCanBeCaptured() {
        var session = submitted("member-a");
        assertThrows(IllegalArgumentException.class, () -> new MemberResultRetention(
                "member-b", session, "v1", "{}", NOW));
        var progress = new TestSession(UUID.randomUUID().toString(), "member-a", session.getDefinitionId(),
                "MENS", "[]", NOW, NOW.plusMinutes(2));
        assertThrows(IllegalStateException.class, () -> result(progress));
    }

    @Test void newExpiryMigrationHasNoBackfillOrDefaultForHistoricalSubmissions() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V28__add_temporary_result_expiry.sql"))
                .replaceAll("(?m)--.*$", "").strip().toUpperCase(java.util.Locale.ROOT);
        assertTrue(sql.startsWith("ALTER TABLE TD_TEST_SESSION"));
        assertTrue(sql.contains("TEMPORARY_RESULT_EXPIRES_AT DATETIME(6) NULL"));
        assertFalse(java.util.regex.Pattern.compile("\\b(UPDATE|INSERT|DELETE|DROP|TRUNCATE|DEFAULT)\\b").matcher(sql).find());
        assertEquals(1, sql.chars().filter(c -> c == ';').count());
    }

    @Test void migrationOnlyCreatesManagementTableWithBoundedIndependentKeys() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V27__create_member_result_retention.sql"));
        assertTrue(sql.contains("UNIQUE (mall_member_id, saved_slot)"));
        assertTrue(sql.contains("saved_slot BETWEEN 1 AND 5"));
        assertTrue(sql.contains("UNIQUE (mall_member_id, current_marker)"));
        assertTrue(sql.contains("current_marker = 1"));
        assertTrue(sql.contains("FOREIGN KEY (session_id, mall_member_id)"));
        assertTrue(sql.contains("ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("COLLATE=utf8mb4_unicode_ci"));
        String executable = sql.replaceAll("(?m)--.*$", "").strip();
        assertTrue(executable.startsWith("CREATE TABLE td_member_result_retention"));
        assertEquals(1, executable.chars().filter(c -> c == ';').count());
    }
}
