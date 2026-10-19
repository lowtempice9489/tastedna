package com.tastedna.session;

import com.tastedna.definition.domain.QuestionDefinition;
import com.tastedna.definition.domain.QuestionDefinitionId;
import com.tastedna.definition.domain.TestDefinition;
import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.definition.persistence.TestDefinitionRepository;
import com.tastedna.session.api.dto.*;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.domain.TestSession;
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

@ExtendWith(MockitoExtension.class)
class TestSessionServiceTest {

    @Mock
    private TestDefinitionRepository definitionRepository;

    @Mock
    private QuestionDefinitionRepository questionRepository;

    @Mock
    private TestSessionRepository sessionRepository;

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
}
