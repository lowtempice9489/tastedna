package com.tastedna.session;

import com.tastedna.session.api.dto.SaveAnswerRequest;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.persistence.TestSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

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

    @Autowired
    private TestSessionService sessionService;

    @Autowired
    private JsonMapper jsonMapper;

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

    @Test
    void saveAnswerPersistsAnswerAndRevision() throws Exception {

        String definitionId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        String memberId = "answer-test-" + UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(ZoneOffset.UTC);

        String databaseName = jdbcTemplate.execute(
                (java.sql.Connection connection) ->
                        connection.getCatalog()
        );

        assertEquals(
                "tastedna_test",
                databaseName,
                "통합 테스트는 tastedna_test DB에서만 실행해야 합니다."
        );

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
                          'answer-test', 'answer-test', ?, FALSE)
                """,
                    definitionId,
                    definitionId,
                    now
            );

            jdbcTemplate.update("""
                INSERT INTO td_question_definition (
                    definition_id,
                    question_id,
                    display_order,
                    question_payload,
                    dependency_payload,
                    required
                ) VALUES (?, 'DEV_Q01', 1, ?, '{}', TRUE)
                """,
                    definitionId,
                    "{\"kind\":\"DEV_PLACEHOLDER\"}"
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
                    '["DEV_Q01"]', ?, ?, ?
                )
                """,
                    sessionId,
                    memberId,
                    definitionId,
                    now,
                    now.plusMinutes(2),
                    now
            );

            SaveAnswerRequest request = new SaveAnswerRequest(
                    "DEV_Q01",
                    0L,
                    jsonMapper.readTree(
                            "{\"selectedOptionIds\":[\"A\"]}"
                    )
            );

            var response = sessionService.saveAnswer(
                    memberId,
                    UUID.fromString(sessionId),
                    request
            );

            assertEquals(1L, response.revision());

            Long storedSessionRevision =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT revision
                            FROM td_test_session
                            WHERE session_id = ?
                            """,
                            Long.class,
                            sessionId
                    );

            assertEquals(1L, storedSessionRevision);

            Long storedAnswerRevision =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT answer_revision
                            FROM td_session_answer
                            WHERE session_id = ?
                              AND question_id = 'DEV_Q01'
                            """,
                            Long.class,
                            sessionId
                    );

            assertEquals(1L, storedAnswerRevision);

            String storedAnswerPayload =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT answer_payload
                            FROM td_session_answer
                            WHERE session_id = ?
                              AND question_id = 'DEV_Q01'
                            """,
                            String.class,
                            sessionId
                    );

            assertEquals(
                    request.answerPayload(),
                    jsonMapper.readTree(storedAnswerPayload)
            );

            String storedPresentedPayload =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT presented_payload
                            FROM td_session_answer
                            WHERE session_id = ?
                              AND question_id = 'DEV_Q01'
                            """,
                            String.class,
                            sessionId
                    );

            assertEquals(
                    jsonMapper.readTree(
                            "{\"kind\":\"DEV_PLACEHOLDER\"}"
                    ),
                    jsonMapper.readTree(storedPresentedPayload)
            );

            SaveAnswerRequest updateRequest = new SaveAnswerRequest(
                    "DEV_Q01",
                    1L,
                    jsonMapper.readTree(
                            "{\"selectedOptionIds\":[\"B\"]}"
                    )
            );

            var updatedResponse = sessionService.saveAnswer(
                    memberId,
                    UUID.fromString(sessionId),
                    updateRequest
            );

            assertEquals(2L, updatedResponse.revision());

            Long updatedSessionRevision = jdbcTemplate.queryForObject(
                    """
                    SELECT revision
                    FROM td_test_session
                    WHERE session_id = ?
                    """,
                    Long.class,
                    sessionId
            );

            assertEquals(2L, updatedSessionRevision);

            Long updatedAnswerRevision = jdbcTemplate.queryForObject(
                    """
                    SELECT answer_revision
                    FROM td_session_answer
                    WHERE session_id = ?
                      AND question_id = 'DEV_Q01'
                    """,
                    Long.class,
                    sessionId
            );

            assertEquals(2L, updatedAnswerRevision);

            String updatedAnswerPayload = jdbcTemplate.queryForObject(
                    """
                    SELECT answer_payload
                    FROM td_session_answer
                    WHERE session_id = ?
                      AND question_id = 'DEV_Q01'
                    """,
                    String.class,
                    sessionId
            );

            assertEquals(
                    updateRequest.answerPayload(),
                    jsonMapper.readTree(updatedAnswerPayload)
            );

            Integer answerCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM td_session_answer
                    WHERE session_id = ?
                      AND question_id = 'DEV_Q01'
                    """,
                    Integer.class,
                    sessionId
            );

            assertEquals(1, answerCount);

            SaveAnswerRequest staleRequest = new SaveAnswerRequest(
                    "DEV_Q01",
                    1L,
                    jsonMapper.readTree(
                            "{\"selectedOptionIds\":[\"C\"]}"
                    )
            );

            org.springframework.web.server.ResponseStatusException exception =
                    assertThrows(
                            org.springframework.web.server.ResponseStatusException.class,
                            () -> sessionService.saveAnswer(
                                    memberId,
                                    UUID.fromString(sessionId),
                                    staleRequest
                            )
                    );

            assertEquals(409, exception.getStatusCode().value());

            assertEquals(
                    "SESSION_REVISION_CONFLICT",
                    exception.getReason()
            );

            Long revisionAfterConflict = jdbcTemplate.queryForObject(
                    """
                    SELECT revision
                    FROM td_test_session
                    WHERE session_id = ?
                    """,
                    Long.class,
                    sessionId
            );

            assertEquals(2L, revisionAfterConflict);

            String payloadAfterConflict = jdbcTemplate.queryForObject(
                    """
                    SELECT answer_payload
                    FROM td_session_answer
                    WHERE session_id = ?
                      AND question_id = 'DEV_Q01'
                    """,
                    String.class,
                    sessionId
            );

            assertEquals(
                    updateRequest.answerPayload(),
                    jsonMapper.readTree(payloadAfterConflict)
            );

            Long answerRevisionAfterConflict = jdbcTemplate.queryForObject(
                    """
                    SELECT answer_revision
                    FROM td_session_answer
                    WHERE session_id = ?
                      AND question_id = 'DEV_Q01'
                    """,
                    Long.class,
                    sessionId
            );

            assertEquals(2L, answerRevisionAfterConflict);

        } finally {

            jdbcTemplate.update(
                    "DELETE FROM td_test_session WHERE session_id = ?",
                    sessionId
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM td_question_definition
                    WHERE definition_id = ?
                    """,
                    definitionId
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM td_test_definition
                    WHERE definition_id = ?
                    """,
                    definitionId
            );
        }
    }

}
