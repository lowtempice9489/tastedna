package com.tastedna.session;

import com.tastedna.session.domain.MemberResultRetention;
import com.tastedna.session.persistence.MemberResultRetentionRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// V28까지 별도로 적용한 테스트 DB에서만 실행하며 새 테스트 행은 매 테스트 종료 시 롤백한다.
@EnabledIfEnvironmentVariable(named = "RUN_RESULT_LIFECYCLE_MYSQL_TESTS", matches = "true")
@SpringBootTest
@Transactional
class MemberResultRetentionJpaTest {
    @Autowired MemberResultRetentionRepository results;
    @Autowired TestSessionRepository sessions;
    @Autowired EntityManager entityManager;

    @DynamicPropertySource
    static void testDatabaseOnly(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:mysql://localhost:3306/tastedna_test?connectionTimeZone=UTC");
        properties.add("spring.flyway.enabled", () -> false);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private MemberResultRetention capture(String member) {
        var session = MemberResultRetentionTest.submitted(member);
        sessions.saveAndFlush(session);
        return MemberResultRetentionTest.result(session);
    }

    @Test void expiryQueryExcludesLegacyProgressOtherMembersAndBothFormsOfProtectionWithoutDeletingHistory() {
        String member = "expiry-" + UUID.randomUUID();
        var expired = capture(member);
        var legacy = capture(member);
        var legacySession = sessions.findById(legacy.getSessionId()).orElseThrow();
        ReflectionTestUtils.setField(legacySession, "temporaryResultExpiresAt", null);
        var saved = capture(member);
        saved.saveToSlot(1, LocalDateTime.now());
        results.saveAndFlush(saved);
        var current = capture(member);
        current.setCurrent(LocalDateTime.now());
        results.saveAndFlush(current);
        var progress = capture(member);
        var progressSession = sessions.findById(progress.getSessionId()).orElseThrow();
        ReflectionTestUtils.setField(progressSession, "status", "IN_PROGRESS");
        ReflectionTestUtils.setField(progressSession, "submittedAt", null);
        ReflectionTestUtils.setField(progressSession, "temporaryResultExpiresAt", null);
        capture("another-" + UUID.randomUUID());
        sessions.flush();
        entityManager.clear();
        var ids = sessions.findTemporaryExpiryReviewIds(member, LocalDateTime.of(2026, 12, 1, 0, 0),
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertEquals(java.util.List.of(expired.getSessionId()), ids);
        assertEquals(5, sessions.findSimpleHistory(member).size());
        assertNotNull(sessions.findById(legacy.getSessionId()).orElseThrow());
    }

    @Test void submissionDoesNotPopulateRetentionAndMembersRemainSeparated() {
        String member = "retention-" + UUID.randomUUID();
        var captured = capture(member);
        assertTrue(results.findByMallMemberIdAndCurrentMarker(member, 1).isEmpty());
        assertTrue(results.findByMallMemberIdAndSavedSlotIsNotNullOrderBySavedAtDesc(member).isEmpty());
        assertTrue(results.findById(captured.getSessionId()).isEmpty());
        captured.setCurrent(LocalDateTime.now());
        results.saveAndFlush(captured);
        entityManager.clear();
        assertTrue(results.findBySessionIdAndMallMemberId(captured.getSessionId(), "another-member").isEmpty());
        assertEquals(captured.getSessionId(), results.findByMallMemberIdAndCurrentMarker(member, 1).orElseThrow().getSessionId());
        assertTrue(results.findByMallMemberIdAndSavedSlotIsNotNullOrderBySavedAtDesc(member).isEmpty());
        assertTrue(sessions.findSimpleHistory(member).stream().anyMatch(s -> s.getSessionId().equals(captured.getSessionId())));
    }

    @Test void sixthSaveCannotReuseOccupiedSlotButSixthSubmissionIsAllowed() {
        String member = "retention-" + UUID.randomUUID();
        for (int slot = 1; slot <= 5; slot++) {
            var result = capture(member);
            result.saveToSlot(slot, LocalDateTime.now());
            results.saveAndFlush(result);
        }
        var sixth = capture(member);
        assertEquals(6, sessions.findSimpleHistory(member).size());
        sixth.saveToSlot(1, LocalDateTime.now());
        assertThrows(DataIntegrityViolationException.class, () -> results.saveAndFlush(sixth));
    }

    @Test void databaseRejectsOutOfRangeSlot() {
        var result = capture("retention-" + UUID.randomUUID());
        result.saveToSlot(1, LocalDateTime.now());
        ReflectionTestUtils.setField(result, "savedSlot", 6);
        assertThrows(DataIntegrityViolationException.class, () -> results.saveAndFlush(result));
    }

    @Test void databaseRejectsTwoCurrentResultsForOneMember() {
        String member = "retention-" + UUID.randomUUID();
        var first = capture(member);
        first.setCurrent(LocalDateTime.now());
        results.saveAndFlush(first);
        var second = capture(member);
        second.setCurrent(LocalDateTime.now());
        assertThrows(DataIntegrityViolationException.class, () -> results.saveAndFlush(second));
    }

    @Test void databaseRejectsMismatchedSessionOwner() {
        var result = capture("retention-" + UUID.randomUUID());
        ReflectionTestUtils.setField(result, "mallMemberId", "another-member");
        assertThrows(DataIntegrityViolationException.class, () -> results.saveAndFlush(result));
    }

    @Test void duplicateSessionCannotCreateSecondRetentionRow() {
        var result = capture("retention-" + UUID.randomUUID());
        results.saveAndFlush(result);
        entityManager.clear();
        var duplicate = MemberResultRetentionTest.result(sessions.findById(result.getSessionId()).orElseThrow());
        assertThrows(DataIntegrityViolationException.class, () -> results.saveAndFlush(duplicate));
    }
}
