package com.tastedna.session;

import com.tastedna.definition.domain.*;
import com.tastedna.definition.persistence.*;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.api.dto.ApparelScope;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.tastedna.session.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageSessionStartTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void startsServerSelectedDefinitionWithoutChangingActiveDefinition(boolean explicitScope) {
        var definitions = mock(TestDefinitionRepository.class);
        var questions = mock(QuestionDefinitionRepository.class);
        var sessions = mock(TestSessionRepository.class);
        var answers = mock(SessionAnswerRepository.class);
        var service = new TestSessionService(definitions, questions, sessions, answers, JsonMapper.builder().build());
        String id = "33333333-3333-4333-8333-333333333333";
        var definition = mock(TestDefinition.class);
        when(definition.getDefinitionId()).thenReturn(id);
        when(definition.getQuestionCount()).thenReturn((short)10);
        when(definition.getTestMode()).thenReturn("SIMPLE");
        when(definitions.findById(id)).thenReturn(Optional.of(definition));
        var rows = new ArrayList<QuestionDefinition>();
        for (int i=1;i<=10;i++) {
            var question = mock(QuestionDefinition.class);
            when(question.getId()).thenReturn(new QuestionDefinitionId(id, "Q%02d".formatted(i)));
            rows.add(question);
        }
        when(questions.findByIdDefinitionIdOrderByDisplayOrderAsc(id)).thenReturn(rows);
        var session = explicitScope ? service.startImageSimple("test-member", ApparelScope.mens)
                : service.startImageSimple("test-member");
        assertEquals(UUID.fromString(id), session.definitionId());
        assertEquals("mens", session.apparelScope().name());
        assertEquals("IN_PROGRESS", session.status());
        verify(definitions, never()).findByTestModeAndActiveTrue(anyString());
        verify(definitions, never()).save(any());
        verify(sessions).saveAndFlush(argThat(saved -> saved.getMallMemberId().equals("test-member")
                && saved.getApparelScope().equals("MENS") && saved.getDefinitionId().equals(id)));
    }
    @Test void womensDraftOrMissingDefinitionCannotCreateSession() {
        var definitions = mock(TestDefinitionRepository.class);
        var questions = mock(QuestionDefinitionRepository.class);
        var sessions = mock(TestSessionRepository.class);
        var answers = mock(SessionAnswerRepository.class);
        var service = new TestSessionService(definitions, questions, sessions, answers, JsonMapper.builder().build());
        var exception = assertThrows(ResponseStatusException.class,
                () -> service.startImageSimple("test-member", ApparelScope.womens));
        assertEquals(422, exception.getStatusCode().value());
        assertEquals("IMAGE_DEFINITION_NOT_READY", exception.getReason());
        var generic = assertThrows(ResponseStatusException.class, () -> service.start("test-member",
                new com.tastedna.session.api.dto.StartSessionRequest(com.tastedna.session.api.dto.Mode.SIMPLE, ApparelScope.womens)));
        assertEquals(422, generic.getStatusCode().value());
        assertEquals("IMAGE_DEFINITION_NOT_READY", generic.getReason());
        // 미완성 여성복을 기존 남성복 또는 모드별 활성 정의로 대체하지 않는다.
        verify(definitions, never()).findByTestModeAndActiveTrue(anyString());
        verify(sessions, never()).saveAndFlush(any());
        verifyNoInteractions(questions, answers);
    }
    @Test void existingInProgressSessionIsNotReplaced() {
        var definitions = mock(TestDefinitionRepository.class);
        var questions = mock(QuestionDefinitionRepository.class);
        var sessions = mock(TestSessionRepository.class);
        var answers = mock(SessionAnswerRepository.class);
        when(sessions.findByMallMemberIdAndStatusIn("test-member", List.of("IN_PROGRESS")))
                .thenReturn(List.of(mock(com.tastedna.session.domain.TestSession.class)));
        var service = new TestSessionService(definitions, questions, sessions, answers, JsonMapper.builder().build());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.startImageSimple("test-member")).getStatusCode().value());
        verifyNoInteractions(definitions, questions, answers);
        verify(sessions, never()).saveAndFlush(any());
    }
}

