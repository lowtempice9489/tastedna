
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

        // 한 회원의 진행 중인 테스트 중복 생성 방지
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

        // 현재 활성화된 테스트 정의 조회
        TestDefinition definition = definitionRepository
                .findByTestModeAndActiveTrue(request.mode().name())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "ACTIVE_DEFINITION_NOT_FOUND"
                ));

        // 테스트 정의에 속한 질문 조회
        List<QuestionDefinition> questions = questionRepository
                .findByIdDefinitionIdOrderByDisplayOrderAsc(
                        definition.getDefinitionId()
                );

        if (questions.size() != definition.getQuestionCount()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
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
            // DB의 활성 회원 유일성 제약도 유지
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
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "SESSION_DEFINITION_NOT_FOUND"
                ));

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
