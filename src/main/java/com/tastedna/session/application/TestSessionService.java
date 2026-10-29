package com.tastedna.session.application;

import com.tastedna.definition.api.dto.QuestionListResponse;
import com.tastedna.definition.api.dto.QuestionResponse;
import com.tastedna.definition.domain.QuestionDefinition;
import com.tastedna.definition.domain.QuestionDefinitionId;
import com.tastedna.definition.domain.TestDefinition;
import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.definition.persistence.TestDefinitionRepository;
import com.tastedna.session.api.dto.*;
import com.tastedna.session.application.comparison.ComparisonAnswerValidator;
import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import com.tastedna.session.application.single.SingleChoiceAnswerValidator;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

// 테스트 세션 생성·조회·재개와 답변 저장을 관리하는 핵심 서비스.
// 회원별 세션 접근, 질문 유효성, 답변 Revision을 검증한다.
@Service
@RequiredArgsConstructor
public class TestSessionService {

    private final TestDefinitionRepository definitionRepository;
    private final QuestionDefinitionRepository questionRepository;
    private final TestSessionRepository sessionRepository;
    private final SessionAnswerRepository answerRepository;
    private final JsonMapper mapper;

    // 진행 중인 세션의 중복 생성만 차단한다.
    // 제출된 세션은 보존하며 새 테스트 시작을 허용한다.
    @Transactional
    public SessionResponse start(
            String memberId,
            StartSessionRequest request) {

        boolean hasActiveSession = !sessionRepository
                .findByMallMemberIdAndStatusIn(
                        memberId,
                        List.of("IN_PROGRESS")
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

        // 세션 시작 당시의 질문 순서를 JSON으로 저장한다.
        // 이후 질문 조회와 답변 저장 시 이 순서를 기준으로 소속 여부를 확인한다.
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
                        List.of("IN_PROGRESS")
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

    // 본인 소유의 진행 중인 세션만 재개할 수 있도록 DB 잠금 후 상태를 검사한다.
    // 재개 시 세션의 임대 만료 시각(leaseUntil)을 갱신한다.
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

    // 세션에 속한 질문의 답변을 검증하고 저장하거나 기존 답변을 수정한다.
    // 예상 Revision이 현재 세션과 다르면 충돌로 처리해 덮어쓰기를 방지한다.
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

        JsonNode questionPayloadNode;

        try {
            questionPayloadNode = mapper.readTree(
                    question.getQuestionPayload()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_QUESTION_DEFINITION",
                    e
            );
        }

        JsonNode kindNode = questionPayloadNode == null
                ? null
                : questionPayloadNode.get("kind");

        String questionKind = kindNode != null
                && kindNode.isString()
                ? kindNode.stringValue()
                : null;

        // SINGLE·MULTIPLE·COMPARISON 유형에 맞는 검증기로 답변 형식을 확인한다.
        // 검증 실패 시 저장하지 않으며, 지원하지 않는 질문 유형도 거부한다.
        if ("SINGLE_CHOICE".equals(questionKind)) {
            try {
                SingleChoiceAnswerValidator.validate(
                        question.getQuestionPayload(),
                        request.answerPayload(),
                        mapper
                );
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        e.getMessage(),
                        e
                );
            }

        } else if ("MULTIPLE_CHOICE".equals(questionKind)) {
            try {
                MultiChoiceAnswerValidator.validate(
                        question.getQuestionPayload(),
                        request.answerPayload(),
                        mapper
                );
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        e.getMessage(),
                        e
                );
            }

        } else if ("COMPARISON".equals(questionKind)) {
            try {
                ComparisonAnswerValidator.validate(
                        question.getQuestionPayload(),
                        request.answerPayload(),
                        mapper
                );
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        e.getMessage(),
                        e
                );
            }

        }else if (!"DEV_PLACEHOLDER".equals(questionKind)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "UNSUPPORTED_QUESTION_KIND"
            );
        }

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

        // 같은 질문에 답변이 있으면 새 Revision으로 수정하고, 없으면 새로 저장한다.
        // 답변과 함께 당시 사용자에게 제시된 질문 내용도 보관한다.
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

    // Simple 세션의 모든 질문에 답변한 경우에만 제출한다.
    // 회원 소유권과 Revision을 확인하며, 제출은 RESULT 확정과 구분한다.
    @Transactional
    public SessionResponse submitSimple(
            String memberId,
            UUID sessionId,
            long expectedRevision
    ) {
        // 기존 답변 저장과 동일하게 세션 행을 잠가 동시 변경을 방지한다.
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
                    "SESSION_NOT_SUBMITTABLE"
            );
        }

        if (session.getRevision() != expectedRevision) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "SESSION_REVISION_CONFLICT"
            );
        }

        TestDefinition definition = definitionRepository
                .findById(session.getDefinitionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "SESSION_DEFINITION_NOT_FOUND"
                ));

        if (!"SIMPLE".equals(definition.getTestMode())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SIMPLE_SESSION_REQUIRED"
            );
        }

        // 세션 시작 시 배정된 질문 목록을 제출 완성도 기준으로 사용한다.
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

        Set<String> requiredQuestionIds = Set.of(
                "Q01", "Q02", "Q03", "Q04", "Q05",
                "Q06", "Q07", "Q08", "Q09", "Q10"
        );

        if (questionOrder.length != requiredQuestionIds.size()
                || !new HashSet<>(Arrays.asList(questionOrder))
                .equals(requiredQuestionIds)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SIMPLE_QUESTION_SET_INVALID"
            );
        }

        Set<String> answeredQuestionIds = answerRepository
                .findByIdSessionIdOrderByIdQuestionIdAsc(
                        sessionId.toString()
                )
                .stream()
                .map(answer -> answer.getId().getQuestionId())
                .collect(java.util.stream.Collectors.toSet());

        if (!answeredQuestionIds.containsAll(requiredQuestionIds)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SIMPLE_ANSWERS_INCOMPLETE"
            );
        }

        // 답변을 수정하지 않고 제출 상태와 제출 시각만 기록한다.
        LocalDateTime now = LocalDateTime.ofInstant(
                Instant.now(),
                ZoneOffset.UTC
        );

        session.submit(now);

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

    // 세션 엔티티를 API 응답에 필요한 정보만 담은 SessionResponse로 변환한다.
    // DB에 저장된 임대 만료 시각은 UTC 기준 Instant로 변환한다.
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
