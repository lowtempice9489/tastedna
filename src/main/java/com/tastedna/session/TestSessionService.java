
package com.tastedna.session;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class TestSessionService {

    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    public TestSessionService(
            JdbcTemplate jdbc,
            JsonMapper mapper) {

        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public SessionDtos.SessionResponse start(
            String memberId,
            SessionDtos.StartRequest request) {

        List<String> active = jdbc.queryForList("""
            SELECT session_id
            FROM td_test_session
            WHERE mall_member_id = ?
              AND status IN ('IN_PROGRESS', 'SUBMITTED')
            """, String.class, memberId);

        if (!active.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_SESSION_EXISTS");
        }

        var definitions = jdbc.query("""
            SELECT definition_id, question_count
            FROM td_test_definition
            WHERE test_mode = ?
              AND active = 1
            """,
                (rs, row) -> new Definition(
                        rs.getString("definition_id"),
                        rs.getInt("question_count")),
                request.mode().name()
        );

        if (definitions.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "ACTIVE_DEFINITION_NOT_FOUND");
        }

        Definition definition = definitions.get(0);

        List<String> questionIds = jdbc.queryForList("""
            SELECT question_id
            FROM td_question_definition
            WHERE definition_id = ?
            ORDER BY display_order
            """,
                String.class,
                definition.id()
        );

        if (questionIds.size() != definition.count()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "QUESTION_SET_INCOMPLETE");
        }

        String questionOrder =
                mapper.writeValueAsString(questionIds);

        UUID sessionId = UUID.randomUUID();

        Instant now = Instant.now();
        Instant leaseUntil = now.plusSeconds(120);

        LocalDateTime dbNow =
                LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        LocalDateTime dbLease =
                LocalDateTime.ofInstant(
                        leaseUntil, ZoneOffset.UTC);

        try {
            jdbc.update("""
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
                )
                VALUES (
                    ?, ?, ?, ?, 'IN_PROGRESS',
                    0, ?, ?, ?, ?
                )
                """,
                    sessionId.toString(),
                    memberId,
                    definition.id(),
                    request.apparelScope().name().toUpperCase(),
                    questionOrder,
                    Timestamp.valueOf(dbNow),
                    Timestamp.valueOf(dbLease),
                    Timestamp.valueOf(dbNow)
            );

        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_SESSION_EXISTS",
                    e);
        }

        return new SessionDtos.SessionResponse(
                sessionId,
                request.mode(),
                request.apparelScope(),
                "IN_PROGRESS",
                0,
                UUID.fromString(definition.id()),
                leaseUntil
        );
    }


    @Transactional(readOnly = true)
    public SessionDtos.SessionResponse get(
            String memberId,
            UUID sessionId) {

        var rows = jdbc.query("""
            SELECT
                s.session_id,
                s.definition_id,
                d.test_mode,
                s.apparel_scope,
                s.status,
                s.revision,
                s.lease_until
            FROM td_test_session s
            JOIN td_test_definition d
                ON s.definition_id = d.definition_id
            WHERE s.session_id = ?
              AND s.mall_member_id = ?
            """,
                (rs, rowNum) -> new SessionDtos.SessionResponse(
                        UUID.fromString(rs.getString("session_id")),
                        SessionDtos.Mode.valueOf(rs.getString("test_mode")),
                        SessionDtos.ApparelScope.valueOf(
                                rs.getString("apparel_scope").toLowerCase()
                        ),
                        rs.getString("status"),
                        rs.getLong("revision"),
                        UUID.fromString(rs.getString("definition_id")),
                        rs.getTimestamp("lease_until")
                                .toLocalDateTime()
                                .toInstant(ZoneOffset.UTC)
                ),
                sessionId.toString(),
                memberId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "SESSION_NOT_FOUND"
            );
        }

        return rows.get(0);
    }


    private record Definition(
            String id,
            int count
    ) {}
}
