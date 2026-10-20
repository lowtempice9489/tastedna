package com.tastedna.session.application;

import com.tastedna.definition.api.dto.QuestionListResponse;
import com.tastedna.definition.api.dto.QuestionResponse;
import com.tastedna.definition.domain.QuestionDefinition;
import com.tastedna.definition.domain.QuestionDefinitionId;
import com.tastedna.definition.domain.TestDefinition;
import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.definition.persistence.TestDefinitionRepository;
import com.tastedna.session.api.dto.*;
import com.tastedna.session.domain.SessionAnswer;
import com.tastedna.session.domain.SessionAnswerId;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.persistence.TestSessionRepository;
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
import java.util.*;

@Service
@RequiredArgsConstructor
public class TestSessionService {

    private final TestDefinitionRepository definitionRepository;
    private final QuestionDefinitionRepository questionRepository;
    private final TestSessionRepository sessionRepository;
    private final SessionAnswerRepository answerRepository;
    private final JsonMapper mapper;

    @Transactional
    public SessionResponse start(
            String memberId,
            StartSessionRequest request) {

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
    public SessionResponse get(
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
    public Optional<SessionResponse> findActive(
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
    public SessionResponse resume(
            String memberId,
            UUID sessionId) {

        TestSession session = sessionRepository
                .findByIdAndMemberIdForUpdate(
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

    @Transactional
    public SessionResponse saveAnswer(
            String memberId,
            UUID sessionId,
            SaveAnswerRequest request) {

        TestSession session = sessionRepository
                .findByIdAndMemberIdForUpdate(
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
                    "SESSION_NOT_EDITABLE"
            );
        }

        if (session.getRevision() != request.expectedRevision()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "SESSION_REVISION_CONFLICT"
            );
        }

        String[] questionOrder;

        try {
            questionOrder = mapper.readValue(
                    session.getQuestionOrder(),
                    String[].class
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_SESSION_QUESTION_ORDER",
                    e
            );
        }

        if (!Arrays.asList(questionOrder)
                .contains(request.questionId())) {

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "QUESTION_NOT_IN_SESSION"
            );
        }

        QuestionDefinitionId questionDefinitionId =
                new QuestionDefinitionId(
                        session.getDefinitionId(),
                        request.questionId()
                );

        QuestionDefinition question = questionRepository
                .findById(questionDefinitionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "QUESTION_DEFINITION_NOT_FOUND"
                ));

        SessionAnswerId answerId = new SessionAnswerId(
                session.getSessionId(),
                request.questionId()
        );

        String answerPayload =
                request.answerPayload().toString();

        String presentedPayload =
                question.getQuestionPayload();

        Instant now = Instant.now();

        LocalDateTime dbNow =
                LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        session.advanceRevision(request.expectedRevision());

        long newRevision = session.getRevision();

        Optional<SessionAnswer> existingAnswer =
                answerRepository.findById(answerId);

        if (existingAnswer.isPresent()) {
            existingAnswer.get().update(
                    answerPayload,
                    presentedPayload,
                    newRevision,
                    dbNow
            );
        } else {
            SessionAnswer answer = new SessionAnswer(
                    session.getSessionId(),
                    request.questionId(),
                    answerPayload,
                    presentedPayload,
                    newRevision,
                    dbNow
            );

            answerRepository.save(answer);
        }

        TestDefinition definition = definitionRepository
                .findById(session.getDefinitionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "SESSION_DEFINITION_NOT_FOUND"
                ));

        return toResponse(session, definition.getTestMode());
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(
            String memberId,
            UUID sessionId,
            String questionId) {

        TestSession session = sessionRepository
                .findBySessionIdAndMallMemberId(
                        sessionId.toString(),
                        memberId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND"
                ));

        String[] questionOrder;

        try {
            questionOrder = mapper.readValue(
                    session.getQuestionOrder(),
                    String[].class
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_SESSION_QUESTION_ORDER",
                    e
            );
        }

        int questionIndex = Arrays.asList(questionOrder)
                .indexOf(questionId);

        if (questionIndex < 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "QUESTION_NOT_IN_SESSION"
            );
        }

        QuestionDefinition question = questionRepository
                .findByIdDefinitionIdAndIdQuestionId(
                        session.getDefinitionId(),
                        questionId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "QUESTION_DEFINITION_NOT_FOUND"
                ));

        try {
            return new QuestionResponse(
                    questionId,
                    (short) (questionIndex + 1),
                    question.isRequired(),
                    mapper.readTree(question.getQuestionPayload())
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_QUESTION_PAYLOAD",
                    e
            );
        }
    }

    @Transactional(readOnly = true)
    public QuestionListResponse getQuestions(
            String memberId,
            UUID sessionId
    ) {
        TestSession session = sessionRepository
                .findBySessionIdAndMallMemberId(
                        sessionId.toString(),
                        memberId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "SESSION_NOT_FOUND"
                ));

        String[] questionOrder;

        try {
            questionOrder = mapper.readValue(
                    session.getQuestionOrder(),
                    String[].class
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_SESSION_QUESTION_ORDER",
                    e
            );
        }

        List<QuestionResponse> questions = new ArrayList<>();

        for (int i = 0; i < questionOrder.length; i++) {
            String questionId = questionOrder[i];

            QuestionDefinition question = questionRepository
                    .findByIdDefinitionIdAndIdQuestionId(
                            session.getDefinitionId(),
                            questionId
                    )
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "QUESTION_DEFINITION_NOT_FOUND"
                    ));

            try {
                questions.add(new QuestionResponse(
                        questionId,
                        (short) (i + 1),
                        question.isRequired(),
                        mapper.readTree(question.getQuestionPayload())
                ));
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "INVALID_QUESTION_PAYLOAD",
                        e
                );
            }
        }

        return new QuestionListResponse(
                sessionId,
                questions
        );
    }

    private SessionResponse toResponse(
            TestSession session,
            String testMode) {

        Instant leaseUntil = session.getLeaseUntil()
                .toInstant(ZoneOffset.UTC);

        return new SessionResponse(
                UUID.fromString(session.getSessionId()),
                Mode.valueOf(testMode),
                ApparelScope.valueOf(
                        session.getApparelScope().toLowerCase()
                ),
                session.getStatus(),
                session.getRevision(),
                UUID.fromString(session.getDefinitionId()),
                leaseUntil
        );
    }
}
