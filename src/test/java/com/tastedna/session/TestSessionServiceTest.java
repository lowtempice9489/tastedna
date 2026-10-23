package com.tastedna.session;

import com.tastedna.definition.domain.QuestionDefinition;
import com.tastedna.definition.domain.QuestionDefinitionId;
import com.tastedna.definition.domain.TestDefinition;
import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.definition.persistence.TestDefinitionRepository;
import com.tastedna.session.api.dto.*;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// TestSessionService의 세션 생성·조회·재개·답변 저장 정책을 검증하는 단위 테스트다.
// Repository와 JsonMapper를 Mock으로 대체하므로 실제 DB 동작은 검증하지 않는다.
@ExtendWith(MockitoExtension.class)
class TestSessionServiceTest {

    @Mock
    private TestDefinitionRepository definitionRepository;

    @Mock
    private QuestionDefinitionRepository questionRepository;

    @Mock
    private TestSessionRepository sessionRepository;

    @Mock
    private SessionAnswerRepository answerRepository;

    @Mock
    private JsonMapper mapper;

    @InjectMocks
    private TestSessionService service;

    private StartSessionRequest request;

    @BeforeEach
    void setUp() {
        request = new StartSessionRequest(
                Mode.SIMPLE,
                ApparelScope.mens
        );
    }

    // IN_PROGRESS 또는 SUBMITTED 세션이 이미 있으면 새 세션 생성을 차단한다.
    // 중복 세션 발견 시 테스트 정의 조회나 세션 저장을 진행하지 않아야 한다.
    @Test
    void existingActiveSessionReturns409() {

        when(sessionRepository.findByMallMemberIdAndStatusIn(
                eq("dev-member-001"),
                eq(List.of("IN_PROGRESS", "SUBMITTED"))
        )).thenReturn(List.of(mock(TestSession.class)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.start("dev-member-001", request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("ACTIVE_SESSION_EXISTS", exception.getReason());

        verifyNoInteractions(definitionRepository, questionRepository);
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingActiveDefinitionReturns422() {

        when(sessionRepository.findByMallMemberIdAndStatusIn(
                eq("dev-member-001"),
                anyList()
        )).thenReturn(List.of());

        when(definitionRepository.findByTestModeAndActiveTrue("SIMPLE"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.start("dev-member-001", request)
        );

        assertEquals(
                422,
                exception.getStatusCode().value()
        );

        assertEquals(
                "ACTIVE_DEFINITION_NOT_FOUND",
                exception.getReason()
        );

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownSessionReturns404() {

        UUID sessionId = UUID.randomUUID();

        when(sessionRepository.findBySessionIdAndMallMemberId(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.get("dev-member-001", sessionId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("SESSION_NOT_FOUND", exception.getReason());
    }

    @Test
    void existingSessionReturnsCorrectData() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.of(2026, 10, 16, 10, 0);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        TestDefinition definition = mock(TestDefinition.class);

        when(sessionRepository.findBySessionIdAndMallMemberId(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SessionResponse response =
                service.get("dev-member-001", sessionId);

        assertEquals(sessionId, response.sessionId());
        assertEquals(definitionId, response.definitionId());
        assertEquals("SIMPLE", response.mode().name());
        assertEquals("mens", response.apparelScope().name());
        assertEquals("IN_PROGRESS", response.status());
        assertEquals(0L, response.revision());

        assertNotNull(response.leaseUntil());

        verify(sessionRepository).findBySessionIdAndMallMemberId(
                sessionId.toString(),
                "dev-member-001"
        );
    }

    @Test
    void newSessionIsSavedSuccessfully() {

        TestDefinition definition = mock(TestDefinition.class);
        QuestionDefinition question = mock(QuestionDefinition.class);
        QuestionDefinitionId questionId = mock(QuestionDefinitionId.class);

        String definitionId =
                "11111111-1111-4111-8111-111111111111";

        when(sessionRepository.findByMallMemberIdAndStatusIn(
                eq("dev-member-001"),
                eq(List.of("IN_PROGRESS", "SUBMITTED"))
        )).thenReturn(List.of());

        when(definitionRepository.findByTestModeAndActiveTrue("SIMPLE"))
                .thenReturn(Optional.of(definition));

        when(definition.getDefinitionId())
                .thenReturn(definitionId);

        when(definition.getQuestionCount())
                .thenReturn((short) 1);

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        when(questionRepository.findByIdDefinitionIdOrderByDisplayOrderAsc(
                definitionId
        )).thenReturn(List.of(question));

        when(question.getId())
                .thenReturn(questionId);

        when(questionId.getQuestionId())
                .thenReturn("DEV_Q01");

        when(mapper.writeValueAsString(List.of("DEV_Q01")))
                .thenReturn("[\"DEV_Q01\"]");

        SessionResponse response =
                service.start("dev-member-001", request);

        var captor = org.mockito.ArgumentCaptor.forClass(
                TestSession.class
        );

        verify(sessionRepository).saveAndFlush(captor.capture());

        TestSession saved = captor.getValue();

        assertEquals("dev-member-001", saved.getMallMemberId());
        assertEquals(definitionId, saved.getDefinitionId());
        assertEquals("MENS", saved.getApparelScope());
        assertEquals("IN_PROGRESS", saved.getStatus());
        assertEquals(0L, saved.getRevision());
        assertEquals("[\"DEV_Q01\"]", saved.getQuestionOrder());

        assertEquals(
                UUID.fromString(saved.getSessionId()),
                response.sessionId()
        );
        assertEquals("SIMPLE", response.mode().name());
        assertEquals("mens", response.apparelScope().name());
        assertEquals("IN_PROGRESS", response.status());
        assertEquals(0L, response.revision());
        assertNotNull(response.leaseUntil());
    }

    // 현재 활성 세션 조회는 leaseUntil 만료 여부가 아니라 세션 상태를 기준으로 한다.
    // 임대 시간이 지나도 IN_PROGRESS 상태라면 조회 결과에 포함되는 현재 정책을 검증한다.
    @Test
    void activeSessionCanBeFoundAfterLeaseExpires() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime past =
                LocalDateTime.now(java.time.Clock.systemUTC())
                        .minusHours(1);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                past,
                past.plusMinutes(2)
        );

        TestDefinition definition = mock(TestDefinition.class);

        when(sessionRepository.findByMallMemberIdAndStatusIn(
                "dev-member-001",
                List.of("IN_PROGRESS", "SUBMITTED")
        )).thenReturn(List.of(session));

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        Optional<SessionResponse> result =
                service.findActive("dev-member-001");

        assertTrue(result.isPresent());
        assertEquals(sessionId, result.get().sessionId());
        assertEquals("IN_PROGRESS", result.get().status());
        assertTrue(result.get().leaseUntil().isBefore(
                java.time.Instant.now()
        ));

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void noActiveSessionReturnsEmpty() {

        when(sessionRepository.findByMallMemberIdAndStatusIn(
                "dev-member-001",
                List.of("IN_PROGRESS", "SUBMITTED")
        )).thenReturn(List.of());

        Optional<SessionResponse> result =
                service.findActive("dev-member-001");

        assertTrue(result.isEmpty());

        verifyNoInteractions(definitionRepository, questionRepository);
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void resumeSessionUpdatesLeaseSuccessfully() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime past =
                LocalDateTime.now(java.time.Clock.systemUTC())
                        .minusHours(1);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                past,
                past.plusMinutes(2)
        );

        TestDefinition definition = mock(TestDefinition.class);

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SessionResponse response =
                service.resume("dev-member-001", sessionId);

        assertEquals(sessionId, response.sessionId());
        assertEquals("IN_PROGRESS", response.status());
        assertEquals(0L, response.revision());

        assertTrue(session.getLastSeenAt().isAfter(past));
        assertTrue(session.getLeaseUntil().isAfter(
                session.getLastSeenAt()
        ));

        assertEquals(
                session.getLeaseUntil()
                        .toInstant(java.time.ZoneOffset.UTC),
                response.leaseUntil()
        );
    }

    // 현재 세션보다 오래된 Revision으로 답변 저장을 요청하면 HTTP 409를 반환한다.
    // 충돌 시 세션 Revision을 변경하거나 답변 Repository에 접근하지 않아야 한다.
    @Test
    void saveAnswerRejectsStaleRevision() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.of(2026, 10, 19, 10, 0);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        session.advanceRevision(0L);

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "DEV_Q01",
                0L,
                tools.jackson.databind.json.JsonMapper
                        .builder()
                        .build()
                        .readTree("{\"selectedOptionIds\":[\"A\"]}")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "SESSION_REVISION_CONFLICT",
                exception.getReason()
        );

        assertEquals(1L, session.getRevision());

        verifyNoInteractions(answerRepository);
        verifyNoInteractions(questionRepository);
    }

    // 처음 저장하는 답변은 새 SessionAnswer로 생성하고 세션 Revision을 증가시킨다.
    // 사용자 답변과 당시 질문 정의가 함께 저장되는지도 확인한다.
    @Test
    void saveAnswerCreatesAnswerAndAdvancesRevision() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.of(2026, 10, 19, 10, 0);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"DEV_Q01\"]",
                String[].class
        )).thenReturn(new String[]{"DEV_Q01"});

        QuestionDefinition question = mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "DEV_Q01"
                )
        )).thenReturn(Optional.of(question));

        when(question.getQuestionPayload())
                .thenReturn("{\"kind\":\"DEV_PLACEHOLDER\"}");

        when(mapper.readTree("{\"kind\":\"DEV_PLACEHOLDER\"}"))
                .thenReturn(
                        JsonMapper.builder()
                                .build()
                                .readTree("{\"kind\":\"DEV_PLACEHOLDER\"}")
                );

        TestDefinition definition = mock(TestDefinition.class);

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SaveAnswerRequest request = new SaveAnswerRequest(
                "DEV_Q01",
                0L,
                JsonMapper.builder()
                        .build()
                        .readTree("{\"selectedOptionIds\":[\"A\"]}")
        );

        SessionResponse response = service.saveAnswer(
                "dev-member-001",
                sessionId,
                request
        );

        assertEquals(1L, response.revision());
        assertEquals(1L, session.getRevision());
        assertEquals("IN_PROGRESS", response.status());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.tastedna.session.domain.SessionAnswer.class
        );

        verify(answerRepository).save(captor.capture());

        var saved = captor.getValue();

        assertEquals(sessionId.toString(), saved.getId().getSessionId());
        assertEquals("DEV_Q01", saved.getId().getQuestionId());
        assertEquals(1L, saved.getAnswerRevision());

        assertEquals(
                "{\"kind\":\"DEV_PLACEHOLDER\"}",
                saved.getPresentedPayload()
        );

        assertEquals(
                "{\"selectedOptionIds\":[\"A\"]}",
                saved.getAnswerPayload()
        );

        assertNotNull(saved.getAnsweredAt());
    }

    // 동일 질문에 기존 답변이 있으면 새 레코드 대신 기존 엔티티를 수정한다.
    // 답변 내용과 Revision이 갱신되는지 확인해 중복 저장을 방지하는 정책을 검증한다.
    @Test
    void saveAnswerUpdatesExistingAnswerAndAdvancesRevision() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(java.time.Clock.systemUTC())
                        .minusHours(1);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        session.advanceRevision(0L);

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"DEV_Q01\"]",
                String[].class
        )).thenReturn(new String[]{"DEV_Q01"});

        QuestionDefinition question = mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "DEV_Q01"
                )
        )).thenReturn(Optional.of(question));

        when(question.getQuestionPayload())
                .thenReturn("{\"kind\":\"DEV_PLACEHOLDER\"}");

        when(mapper.readTree("{\"kind\":\"DEV_PLACEHOLDER\"}"))
                .thenReturn(
                        JsonMapper.builder()
                                .build()
                                .readTree("{\"kind\":\"DEV_PLACEHOLDER\"}")
                );

        com.tastedna.session.domain.SessionAnswer existingAnswer =
                new com.tastedna.session.domain.SessionAnswer(
                        sessionId.toString(),
                        "DEV_Q01",
                        "{\"selectedOptionIds\":[\"A\"]}",
                        "{\"kind\":\"DEV_PLACEHOLDER\"}",
                        1L,
                        now
                );

        when(answerRepository.findById(
                new com.tastedna.session.domain.SessionAnswerId(
                        sessionId.toString(),
                        "DEV_Q01"
                )
        )).thenReturn(Optional.of(existingAnswer));

        TestDefinition definition = mock(TestDefinition.class);

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SaveAnswerRequest request = new SaveAnswerRequest(
                "DEV_Q01",
                1L,
                JsonMapper.builder()
                        .build()
                        .readTree("{\"selectedOptionIds\":[\"B\"]}")
        );

        SessionResponse response = service.saveAnswer(
                "dev-member-001",
                sessionId,
                request
        );

        assertEquals(2L, response.revision());
        assertEquals(2L, session.getRevision());

        assertEquals(2L, existingAnswer.getAnswerRevision());

        assertEquals(
                "{\"selectedOptionIds\":[\"B\"]}",
                existingAnswer.getAnswerPayload()
        );

        assertEquals(
                "{\"kind\":\"DEV_PLACEHOLDER\"}",
                existingAnswer.getPresentedPayload()
        );

        assertTrue(existingAnswer.getAnsweredAt().isAfter(now));

        verify(answerRepository, never()).save(any());
    }

    @Test
    void saveAnswerRejectsQuestionNotInSession() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(java.time.Clock.systemUTC());

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"DEV_Q01\"]",
                String[].class
        )).thenReturn(new String[]{"DEV_Q01"});

        SaveAnswerRequest request = new SaveAnswerRequest(
                "DEV_Q99",
                0L,
                JsonMapper.builder()
                        .build()
                        .readTree("{\"selectedOptionIds\":[\"A\"]}")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                422,
                exception.getStatusCode().value()
        );

        assertEquals(
                "QUESTION_NOT_IN_SESSION",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
        verifyNoInteractions(questionRepository);
    }

    @Test
    void saveAnswerRejectsSessionOwnedByAnotherMember() {

        UUID sessionId = UUID.randomUUID();

        String requestingMemberId = "mall-member-B";

        SaveAnswerRequest request = new SaveAnswerRequest(
                "DEV_Q01",
                0L,
                JsonMapper.builder()
                        .build()
                        .readTree("{\"selectedOptionIds\":[\"A\"]}")
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                requestingMemberId
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        requestingMemberId,
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "SESSION_NOT_FOUND",
                exception.getReason()
        );

        verify(sessionRepository).findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                requestingMemberId
        );

        verifyNoInteractions(answerRepository);
        verifyNoInteractions(questionRepository);
        verifyNoInteractions(definitionRepository);
    }

    @Test
    void saveAnswerRejectsSubmittedSession() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.of(2026, 10, 19, 10, 0);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        org.springframework.test.util.ReflectionTestUtils.setField(
                session,
                "status",
                "SUBMITTED"
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "DEV_Q01",
                0L,
                JsonMapper.builder()
                        .build()
                        .readTree("{\"selectedOptionIds\":[\"A\"]}")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "SESSION_NOT_EDITABLE",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
        verifyNoInteractions(questionRepository);
        verifyNoInteractions(definitionRepository);
    }

    @Test
    void saveAnswerRejectsMissingQuestionDefinition() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(java.time.Clock.systemUTC());

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"DEV_Q01\"]",
                String[].class
        )).thenReturn(new String[]{"DEV_Q01"});

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "DEV_Q01"
                )
        )).thenReturn(Optional.empty());

        SaveAnswerRequest request = new SaveAnswerRequest(
                "DEV_Q01",
                0L,
                JsonMapper.builder()
                        .build()
                        .readTree("{\"selectedOptionIds\":[\"A\"]}")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                422,
                exception.getStatusCode().value()
        );

        assertEquals(
                "QUESTION_DEFINITION_NOT_FOUND",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
        verifyNoInteractions(definitionRepository);
    }

    @Test
    void getQuestionRejectsSessionOwnedByAnotherMember() {

        UUID sessionId = UUID.randomUUID();

        String requestingMemberId = "another-mall-member";

        when(sessionRepository.findBySessionIdAndMallMemberId(
                sessionId.toString(),
                requestingMemberId
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getQuestion(
                        requestingMemberId,
                        sessionId,
                        "DEV_Q01"
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "SESSION_NOT_FOUND",
                exception.getReason()
        );

        verify(sessionRepository).findBySessionIdAndMallMemberId(
                sessionId.toString(),
                requestingMemberId
        );

        verifyNoInteractions(questionRepository);
        verifyNoInteractions(definitionRepository);
        verifyNoInteractions(answerRepository);
    }

    @Test
    void getQuestionReturnsQuestionInSessionOrder() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.of(2026, 10, 20, 9, 0);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q02\",\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findBySessionIdAndMallMemberId(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"DEV_Q02\",\"DEV_Q01\"]",
                String[].class
        )).thenReturn(new String[]{"DEV_Q02", "DEV_Q01"});

        QuestionDefinition question = mock(QuestionDefinition.class);

        when(questionRepository.findByIdDefinitionIdAndIdQuestionId(
                definitionId.toString(),
                "DEV_Q01"
        )).thenReturn(Optional.of(question));

        when(question.getQuestionPayload())
                .thenReturn("{\"kind\":\"DEV_PLACEHOLDER\",\"label\":\"개발용 질문 01\"}");

        when(mapper.readTree(question.getQuestionPayload()))
                .thenReturn(
                        JsonMapper.builder().build().readTree(
                                "{\"kind\":\"DEV_PLACEHOLDER\",\"label\":\"개발용 질문 01\"}"
                        )
                );

        var response = service.getQuestion(
                "dev-member-001",
                sessionId,
                "DEV_Q01"
        );

        assertEquals("DEV_Q01", response.questionId());
        assertEquals(2, response.displayOrder());

        assertEquals(
                "DEV_PLACEHOLDER",
                response.questionPayload().get("kind").stringValue()
        );

        assertEquals(
                "개발용 질문 01",
                response.questionPayload().get("label").stringValue()
        );
    }

    @Test
    void getQuestionsReturnsQuestionsInSessionOrder() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(java.time.Clock.systemUTC());

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"DEV_Q02\",\"DEV_Q01\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findBySessionIdAndMallMemberId(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"DEV_Q02\",\"DEV_Q01\"]",
                String[].class
        )).thenReturn(new String[]{"DEV_Q02", "DEV_Q01"});

        QuestionDefinition question1 = mock(QuestionDefinition.class);
        QuestionDefinition question2 = mock(QuestionDefinition.class);

        when(questionRepository.findByIdDefinitionIdAndIdQuestionId(
                definitionId.toString(),
                "DEV_Q01"
        )).thenReturn(Optional.of(question1));

        when(questionRepository.findByIdDefinitionIdAndIdQuestionId(
                definitionId.toString(),
                "DEV_Q02"
        )).thenReturn(Optional.of(question2));

        String payload1 =
                "{\"kind\":\"DEV_PLACEHOLDER\",\"label\":\"개발용 질문 01\"}";

        String payload2 =
                "{\"kind\":\"DEV_PLACEHOLDER\",\"label\":\"개발용 질문 02\"}";

        when(question1.getQuestionPayload()).thenReturn(payload1);
        when(question2.getQuestionPayload()).thenReturn(payload2);

        when(mapper.readTree(payload1))
                .thenReturn(JsonMapper.builder().build().readTree(payload1));

        when(mapper.readTree(payload2))
                .thenReturn(JsonMapper.builder().build().readTree(payload2));

        var response = service.getQuestions(
                "dev-member-001",
                sessionId
        );

        assertEquals(sessionId, response.sessionId());
        assertEquals(2, response.questions().size());

        assertEquals(
                "DEV_Q02",
                response.questions().get(0).questionId()
        );

        assertEquals(
                1,
                response.questions().get(0).displayOrder()
        );

        assertEquals(
                "DEV_Q01",
                response.questions().get(1).questionId()
        );

        assertEquals(
                2,
                response.questions().get(1).displayOrder()
        );
    }

    @Test
    void getQuestionsRejectsSessionOwnedByAnotherMember() {

        UUID sessionId = UUID.randomUUID();

        String requestingMemberId = "another-mall-member";

        when(sessionRepository.findBySessionIdAndMallMemberId(
                sessionId.toString(),
                requestingMemberId
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getQuestions(
                        requestingMemberId,
                        sessionId
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "SESSION_NOT_FOUND",
                exception.getReason()
        );

        verify(sessionRepository).findBySessionIdAndMallMemberId(
                sessionId.toString(),
                requestingMemberId
        );

        verifyNoInteractions(questionRepository);
        verifyNoInteractions(definitionRepository);
        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsUnknownSingleChoiceOptionWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
            {
              "kind": "SINGLE_CHOICE",
              "prompt": "검증용 질문",
              "options": [
                {"id": "A", "label": "선택지 A"},
                {"id": "B", "label": "선택지 B"}
              ]
            }
            """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper = JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                    {"selectedOptionId":"G"}
                    """)
        );

        var exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsExtraAnswerFieldWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
            {
              "kind": "SINGLE_CHOICE",
              "prompt": "검증용 질문",
              "options": [
                {"id": "A", "label": "선택지 A"},
                {"id": "B", "label": "선택지 B"}
              ]
            }
            """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper = JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                    {
                      "selectedOptionId": "A",
                      "score": 100
                    }
                    """)
        );

        var exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "INVALID_ANSWER_PAYLOAD",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerAcceptsValidSingleChoiceOptionAndSaves() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "kind": "SINGLE_CHOICE",
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"},
            {"id": "B", "label": "선택지 B"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        TestDefinition definition =
                mock(TestDefinition.class);

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                {"selectedOptionId":"A"}
                """)
        );

        SessionResponse response = service.saveAnswer(
                "dev-member-001",
                sessionId,
                request
        );

        assertEquals(1L, response.revision());
        assertEquals(1L, session.getRevision());
        assertEquals("IN_PROGRESS", response.status());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.tastedna.session.domain.SessionAnswer.class
        );

        verify(answerRepository).save(captor.capture());

        var saved = captor.getValue();

        assertEquals(
                sessionId.toString(),
                saved.getId().getSessionId()
        );

        assertEquals(
                "Q10",
                saved.getId().getQuestionId()
        );

        assertEquals(1L, saved.getAnswerRevision());

        assertEquals(
                "{\"selectedOptionId\":\"A\"}",
                saved.getAnswerPayload()
        );

        assertEquals(
                questionPayload,
                saved.getPresentedPayload()
        );

        assertNotNull(saved.getAnsweredAt());
    }

    @Test
    void saveAnswerUpdatesValidSingleChoiceAnswer() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(java.time.Clock.systemUTC())
                        .minusHours(1);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        session.advanceRevision(0L);

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "kind": "SINGLE_CHOICE",
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"},
            {"id": "B", "label": "선택지 B"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        var existingAnswer =
                new com.tastedna.session.domain.SessionAnswer(
                        sessionId.toString(),
                        "Q10",
                        "{\"selectedOptionId\":\"A\"}",
                        questionPayload,
                        1L,
                        now
                );

        when(answerRepository.findById(
                new com.tastedna.session.domain.SessionAnswerId(
                        sessionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(existingAnswer));

        TestDefinition definition =
                mock(TestDefinition.class);

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                1L,
                realMapper.readTree("""
                {"selectedOptionId":"B"}
                """)
        );

        SessionResponse response = service.saveAnswer(
                "dev-member-001",
                sessionId,
                request
        );

        assertEquals(2L, response.revision());
        assertEquals(2L, session.getRevision());

        assertEquals(2L, existingAnswer.getAnswerRevision());

        assertEquals(
                "{\"selectedOptionId\":\"B\"}",
                existingAnswer.getAnswerPayload()
        );

        assertEquals(
                questionPayload,
                existingAnswer.getPresentedPayload()
        );

        assertTrue(existingAnswer.getAnsweredAt().isAfter(now));

        verify(answerRepository, never()).save(any());
    }

    @Test
    void saveAnswerRejectsInvalidSingleChoiceUpdateWithoutChangingExistingAnswer() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(java.time.Clock.systemUTC())
                        .minusHours(1);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        session.advanceRevision(0L);

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "kind": "SINGLE_CHOICE",
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"},
            {"id": "B", "label": "선택지 B"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        var existingAnswer =
                new com.tastedna.session.domain.SessionAnswer(
                        sessionId.toString(),
                        "Q10",
                        "{\"selectedOptionId\":\"A\"}",
                        questionPayload,
                        1L,
                        now
                );

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                1L,
                realMapper.readTree("""
                {"selectedOptionId":"G"}
                """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getReason()
        );

        assertEquals(1L, session.getRevision());

        assertEquals(1L, existingAnswer.getAnswerRevision());

        assertEquals(
                "{\"selectedOptionId\":\"A\"}",
                existingAnswer.getAnswerPayload()
        );

        assertEquals(
                questionPayload,
                existingAnswer.getPresentedPayload()
        );

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsUnsupportedQuestionKindWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "kind": "UNKNOWN_KIND",
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                {"selectedOptionId":"A"}
                """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "UNSUPPORTED_QUESTION_KIND",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsMissingSelectedOptionIdWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "kind": "SINGLE_CHOICE",
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"},
            {"id": "B", "label": "선택지 B"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                {}
                """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "INVALID_ANSWER_PAYLOAD",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsBlankSelectedOptionIdWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "kind": "SINGLE_CHOICE",
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"},
            {"id": "B", "label": "선택지 B"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                {"selectedOptionId":""}
                """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "INVALID_ANSWER_PAYLOAD",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsNumericSelectedOptionIdWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "kind": "SINGLE_CHOICE",
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"},
            {"id": "B", "label": "선택지 B"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                {"selectedOptionId":123}
                """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "INVALID_ANSWER_PAYLOAD",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsMalformedQuestionDefinitionWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {"kind":"SINGLE_CHOICE","options":[
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        // 실제 JSON 파서로 잘못된 질문 정의를 검증한다.
        JsonMapper realMapper = JsonMapper.builder().build();

        // 서비스의 Mock mapper가 실제 파싱 오류를 재현하도록 설정한다.
        when(mapper.readTree(questionPayload))
                .thenAnswer(invocation ->
                        realMapper.readTree(questionPayload)
                );

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                {"selectedOptionId":"A"}
                """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "INVALID_QUESTION_DEFINITION",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsMissingQuestionKindWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                {"selectedOptionId":"A"}
                """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "UNSUPPORTED_QUESTION_KIND",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsNonStringQuestionKindWithoutSaving() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q10\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q10\"]",
                String[].class
        )).thenReturn(new String[]{"Q10"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q10"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
        {
          "kind": 123,
          "prompt": "검증용 질문",
          "options": [
            {"id": "A", "label": "선택지 A"}
          ]
        }
        """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper =
                JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q10",
                0L,
                realMapper.readTree("""
                {"selectedOptionId":"A"}
                """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "UNSUPPORTED_QUESTION_KIND",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerAcceptsValidMultipleChoiceOptionAndSaves() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q01\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q01\"]",
                String[].class
        )).thenReturn(new String[]{"Q01"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q01"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
            {
              "kind": "MULTIPLE_CHOICE",
              "maxSelections": 2,
              "options": [
                {"id": "A", "label": "스타일 A"},
                {"id": "B", "label": "스타일 B"},
                {"id": "C", "label": "스타일 C"}
              ]
            }
            """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper = JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        // 복수 선택 검증에서도 사용하는 동일한 JSON 파싱 설정
        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        TestDefinition definition =
                mock(TestDefinition.class);

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q01",
                0L,
                realMapper.readTree("""
                    {"selectedOptionIds":["A","C"]}
                    """)
        );

        SessionResponse response = service.saveAnswer(
                "dev-member-001",
                sessionId,
                request
        );

        assertEquals(1L, response.revision());
        assertEquals(1L, session.getRevision());
        assertEquals("IN_PROGRESS", response.status());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.tastedna.session.domain.SessionAnswer.class
        );

        verify(answerRepository).save(captor.capture());

        var saved = captor.getValue();

        assertEquals(
                sessionId.toString(),
                saved.getId().getSessionId()
        );
        assertEquals("Q01", saved.getId().getQuestionId());
        assertEquals(1L, saved.getAnswerRevision());

        assertEquals(
                "{\"selectedOptionIds\":[\"A\",\"C\"]}",
                saved.getAnswerPayload()
        );

        assertEquals(
                questionPayload,
                saved.getPresentedPayload()
        );

        assertNotNull(saved.getAnsweredAt());
    }

    // 복수 선택 답변이 질문 정의의 maxSelections를 초과하면 저장을 거부한다.
    // 검증 실패 시 세션 Revision이 증가하거나 답변이 저장되지 않아야 한다.
    @Test
    void saveAnswerRejectsTooManyMultipleChoiceOptions() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q01\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q01\"]",
                String[].class
        )).thenReturn(new String[]{"Q01"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q01"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
            {
              "kind": "MULTIPLE_CHOICE",
              "maxSelections": 2,
              "options": [
                {"id": "A", "label": "스타일 A"},
                {"id": "B", "label": "스타일 B"},
                {"id": "C", "label": "스타일 C"}
              ]
            }
            """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper = JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q01",
                0L,
                realMapper.readTree("""
                    {"selectedOptionIds":["A","B","C"]}
                    """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "TOO_MANY_OPTIONS_SELECTED",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerRejectsUnknownMultipleChoiceOption() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q01\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q01\"]",
                String[].class
        )).thenReturn(new String[]{"Q01"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q01"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
            {
              "kind": "MULTIPLE_CHOICE",
              "maxSelections": 2,
              "options": [
                {"id": "A", "label": "스타일 A"},
                {"id": "B", "label": "스타일 B"},
                {"id": "C", "label": "스타일 C"}
              ]
            }
            """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper = JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q01",
                0L,
                realMapper.readTree("""
                    {"selectedOptionIds":["A","G"]}
                    """)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswer(
                        "dev-member-001",
                        sessionId,
                        request
                )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getReason()
        );

        assertEquals(0L, session.getRevision());

        verifyNoInteractions(answerRepository);
    }

    @Test
    void saveAnswerUpdatesValidMultipleChoiceAnswer() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now(java.time.Clock.systemUTC())
                        .minusHours(1);

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q01\"]",
                now,
                now.plusMinutes(2)
        );

        session.advanceRevision(0L);

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q01\"]",
                String[].class
        )).thenReturn(new String[]{"Q01"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q01"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
            {
              "kind": "MULTIPLE_CHOICE",
              "maxSelections": 2,
              "options": [
                {"id": "A", "label": "스타일 A"},
                {"id": "B", "label": "스타일 B"},
                {"id": "C", "label": "스타일 C"}
              ]
            }
            """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper = JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        var existingAnswer =
                new com.tastedna.session.domain.SessionAnswer(
                        sessionId.toString(),
                        "Q01",
                        "{\"selectedOptionIds\":[\"A\",\"B\"]}",
                        questionPayload,
                        1L,
                        now
                );

        when(answerRepository.findById(
                new com.tastedna.session.domain.SessionAnswerId(
                        sessionId.toString(),
                        "Q01"
                )
        )).thenReturn(Optional.of(existingAnswer));

        TestDefinition definition =
                mock(TestDefinition.class);

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q01",
                1L,
                realMapper.readTree("""
                    {"selectedOptionIds":["B","C"]}
                    """)
        );

        SessionResponse response = service.saveAnswer(
                "dev-member-001",
                sessionId,
                request
        );

        assertEquals(2L, response.revision());
        assertEquals(2L, session.getRevision());

        assertEquals(2L, existingAnswer.getAnswerRevision());

        assertEquals(
                "{\"selectedOptionIds\":[\"B\",\"C\"]}",
                existingAnswer.getAnswerPayload()
        );

        assertEquals(
                questionPayload,
                existingAnswer.getPresentedPayload()
        );

        assertTrue(existingAnswer.getAnsweredAt().isAfter(now));

        verify(answerRepository, never()).save(any());
    }

    // 비교형 질문의 세 비교쌍에 유효한 선택값이 있으면 답변 저장을 허용한다.
    // 비교쌍별 선택 결과와 당시 질문 정의가 답변 엔티티에 보존되는지 확인한다.
    @Test
    void saveAnswerAcceptsValidComparisonAndSaves() {

        UUID sessionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        TestSession session = new TestSession(
                sessionId.toString(),
                "dev-member-001",
                definitionId.toString(),
                "MENS",
                "[\"Q08\"]",
                now,
                now.plusMinutes(2)
        );

        when(sessionRepository.findByIdAndMemberIdForUpdate(
                sessionId.toString(),
                "dev-member-001"
        )).thenReturn(Optional.of(session));

        when(mapper.readValue(
                "[\"Q08\"]",
                String[].class
        )).thenReturn(new String[]{"Q08"});

        QuestionDefinition question =
                mock(QuestionDefinition.class);

        when(questionRepository.findById(
                new QuestionDefinitionId(
                        definitionId.toString(),
                        "Q08"
                )
        )).thenReturn(Optional.of(question));

        String questionPayload = """
            {
              "kind": "COMPARISON",
              "comparisons": [
                {
                  "id": "C1",
                  "options": [{"id": "A"}, {"id": "B"}]
                },
                {
                  "id": "C2",
                  "options": [{"id": "A"}, {"id": "B"}]
                },
                {
                  "id": "C3",
                  "options": [{"id": "A"}, {"id": "B"}]
                }
              ]
            }
            """;

        when(question.getQuestionPayload())
                .thenReturn(questionPayload);

        JsonMapper realMapper = JsonMapper.builder().build();

        when(mapper.readTree(questionPayload))
                .thenReturn(realMapper.readTree(questionPayload));

        TestDefinition definition =
                mock(TestDefinition.class);

        when(definitionRepository.findById(
                definitionId.toString()
        )).thenReturn(Optional.of(definition));

        when(definition.getTestMode())
                .thenReturn("SIMPLE");

        SaveAnswerRequest request = new SaveAnswerRequest(
                "Q08",
                0L,
                realMapper.readTree("""
                    {
                      "selections": [
                        {"comparisonId": "C1", "selectedOptionId": "A"},
                        {"comparisonId": "C2", "selectedOptionId": "B"},
                        {"comparisonId": "C3", "selectedOptionId": "A"}
                      ]
                    }
                    """)
        );

        SessionResponse response = service.saveAnswer(
                "dev-member-001",
                sessionId,
                request
        );

        assertEquals(1L, response.revision());
        assertEquals(1L, session.getRevision());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.tastedna.session.domain.SessionAnswer.class
        );

        verify(answerRepository).save(captor.capture());

        var saved = captor.getValue();

        assertEquals("Q08", saved.getId().getQuestionId());
        assertEquals(1L, saved.getAnswerRevision());
        assertEquals(questionPayload, saved.getPresentedPayload());

        var storedAnswer = realMapper.readTree(
                saved.getAnswerPayload()
        );

        assertEquals(
                request.answerPayload(),
                storedAnswer
        );
    }

}
