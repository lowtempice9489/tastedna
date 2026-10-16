
package com.tastedna.session;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestSessionService {

    private final TestDefinitionRepository definitionRepository;
    private final QuestionDefinitionRepository questionRepository;
    private final TestSessionRepository sessionRepository;
    private final JsonMapper mapper;

    @Transactional
    public SessionDtos.SessionResponse start(
            String memberId,
            SessionDtos.StartRequest request) {

        boolean hasActiveSession = !sessionRepository
                .findByMallMemberIdAndStatusIn(
                        memberId,
                        List.of("IN_PROGRESS", "SUBMITTED")
                ).isEmpty();

        if (hasActiveSession) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_SESSION_EXISTS"
            );
        }

        TestDefinition definition = definitionRepository
                .findByTestModeAndActiveTrue(request.mode().name())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "ACTIVE_DEFINITION_NOT_FOUND"
                ));

        List<QuestionDefinition> questions = questionRepository
                .findByIdDefinitionIdOrderByDisplayOrderAsc(
                        definition.getDefinitionId()
                );

        if (questions.size() != definition.getQuestionCount()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "QUESTION_SET_INCOMPLETE"
            );
        }

        List<String> questionIds = questions.stream()
                .map(q -> q.getId().getQuestionId())
                .toList();

        String questionOrder = mapper.writeValueAsString(questionIds);

        UUID sessionId = UUID.randomUUID();

        Instant now = Instant.now();
        Instant leaseUntil = now.plusSeconds(120);

        LocalDateTime dbNow =
                LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        LocalDateTime dbLease =
                LocalDateTime.ofInstant(leaseUntil, ZoneOffset.UTC);

        TestSession session = new TestSession(
                sessionId.toString(),
                memberId,
                definition.getDefinitionId(),
                request.apparelScope().name().toUpperCase(),
                questionOrder,
                dbNow,
                dbLease
        );

        try {
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_SESSION_EXISTS",
                    e
            );
        }

        return toResponse(session, definition.getTestMode());
    }

    @Transactional(readOnly = true)
    public SessionDtos.SessionResponse get(
            String memberId,
            UUID sessionId) {

        TestSession session = sessionRepository
                .findBySessionIdAndMallMemberId(
                        sessionId.toString(),
                        memberId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND"
                ));

        TestDefinition definition = definitionRepository
                .findById(session.getDefinitionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "SESSION_DEFINITION_NOT_FOUND"
                ));

        return toResponse(session, definition.getTestMode());
    }

    @Transactional(readOnly = true)
    public Optional<SessionDtos.SessionResponse> findActive(
            String memberId) {

        List<TestSession> activeSessions = sessionRepository
                .findByMallMemberIdAndStatusIn(
                        memberId,
                        List.of("IN_PROGRESS", "SUBMITTED")
                );

        if (activeSessions.isEmpty()) {
            return Optional.empty();
        }

        TestSession session = activeSessions.getFirst();

        TestDefinition definition = definitionRepository
                .findById(session.getDefinitionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "SESSION_DEFINITION_NOT_FOUND"
                ));

        return Optional.of(
                toResponse(session, definition.getTestMode())
        );
    }

    @Transactional
    public SessionDtos.SessionResponse resume(
            String memberId,
            UUID sessionId) {

        TestSession session = sessionRepository
                .findBySessionIdAndMallMemberId(
                        sessionId.toString(),
                        memberId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND"
                ));

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "SESSION_NOT_RESUMABLE"
            );
        }

        TestDefinition definition = definitionRepository
                .findById(session.getDefinitionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "SESSION_DEFINITION_NOT_FOUND"
                ));

        Instant now = Instant.now();

        LocalDateTime dbNow =
                LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        LocalDateTime dbLease =
                LocalDateTime.ofInstant(
                        now.plusSeconds(120),
                        ZoneOffset.UTC
                );

        session.resume(dbNow, dbLease);

        return toResponse(session, definition.getTestMode());
    }

    private SessionDtos.SessionResponse toResponse(
            TestSession session,
            String testMode) {

        Instant leaseUntil = session.getLeaseUntil()
                .toInstant(ZoneOffset.UTC);

        return new SessionDtos.SessionResponse(
                UUID.fromString(session.getSessionId()),
                SessionDtos.Mode.valueOf(testMode),
                SessionDtos.ApparelScope.valueOf(
                        session.getApparelScope().toLowerCase()
                ),
                session.getStatus(),
                session.getRevision(),
                UUID.fromString(session.getDefinitionId()),
                leaseUntil
        );
    }
}
