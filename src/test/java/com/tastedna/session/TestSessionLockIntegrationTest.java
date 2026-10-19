package com.tastedna.session;

import com.tastedna.session.persistence.TestSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TestSessionLockIntegrationTest {

    @Autowired
    private TestSessionRepository sessionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void secondTransactionWaitsForFirstLock() throws Exception {

        String databaseName = jdbcTemplate.execute(
                (java.sql.Connection connection) ->
                        connection.getCatalog()
        );

        assertEquals(
                "tastedna_test",
                databaseName,
                "통합 테스트는 tastedna_test DB에서만 실행해야 합니다."
        );
        String definitionId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        String memberId = "lock-test-" + UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(ZoneOffset.UTC);

        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);

        try {
            jdbcTemplate.update("""
                    INSERT INTO td_test_definition (
                        definition_id,
                        test_mode,
                        version_code,
                        question_count,
                        scoring_version,
                        raw_schema_version,
                        published_at,
                        active
                    ) VALUES (?, 'SIMPLE', ?, 10,
                              'lock-test', 'lock-test', ?, FALSE)
                    """,
                    definitionId,
                    definitionId,
                    now
            );

            jdbcTemplate.update("""
                    INSERT INTO td_test_session (
                        session_id,
                        mall_member_id,
                        definition_id,
                        apparel_scope,
                        status,
                        revision,
                        question_order,
                        last_seen_at,
                        lease_until,
                        started_at
                    ) VALUES (
                        ?, ?, ?, 'MENS', 'IN_PROGRESS', 0,
                        '[]', ?, ?, ?
                    )
                    """,
                    sessionId,
                    memberId,
                    definitionId,
                    now,
                    now.plusMinutes(2),
                    now
            );

            var first = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {

                        sessionRepository.findByIdAndMemberIdForUpdate(
                                sessionId,
                                memberId
                        ).orElseThrow();

                        firstLocked.countDown();

                        try {
                            if (!releaseFirst.await(
                                    10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException(
                                        "FIRST_LOCK_TIMEOUT"
                                );
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    })
            );

            assertTrue(
                    firstLocked.await(10, TimeUnit.SECONDS),
                    "첫 번째 트랜잭션이 잠금을 획득하지 못했습니다."
            );

            var second = executor.submit(() -> {

                secondStarted.countDown();

                transactionTemplate.executeWithoutResult(status -> {
                    sessionRepository.findByIdAndMemberIdForUpdate(
                            sessionId,
                            memberId
                    ).orElseThrow();
                });
            });

            assertTrue(
                    secondStarted.await(10, TimeUnit.SECONDS)
            );

            Thread.sleep(500);

            assertFalse(
                    second.isDone(),
                    "첫 번째 잠금이 유지되는 동안 두 번째 요청이 완료됐습니다."
            );

            releaseFirst.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

        } finally {
            releaseFirst.countDown();

            executor.shutdownNow();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "TEST_THREADS_DID_NOT_TERMINATE"
                );
            }

            jdbcTemplate.update(
                    "DELETE FROM td_test_session WHERE session_id = ?",
                    sessionId
            );

            jdbcTemplate.update(
                    "DELETE FROM td_test_definition WHERE definition_id = ?",
                    definitionId
            );
        }
    }
}
