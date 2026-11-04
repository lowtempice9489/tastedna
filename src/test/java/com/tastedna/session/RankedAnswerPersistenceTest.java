package com.tastedna.session;

import com.tastedna.definition.domain.*;
import com.tastedna.definition.persistence.*;
import com.tastedna.scoring.application.PreferenceSummaryService;
import com.tastedna.session.api.dto.SaveAnswerRequest;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.domain.*;
import com.tastedna.session.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 저장 서비스와 재조회 서비스를 연결하되 Repository만 격리한다. MySQL 검증과는 구분한다.
class RankedAnswerPersistenceTest {
    @Test void savesRestoresAndUpdatesOrderWhileRejectedAnswersLeaveRevisionUntouched() throws Exception {
        var definitions = mock(TestDefinitionRepository.class);
        var questions = mock(QuestionDefinitionRepository.class);
        var sessions = mock(TestSessionRepository.class);
        var answers = mock(SessionAnswerRepository.class);
        var mapper = JsonMapper.builder().build();
        var service = new TestSessionService(definitions, questions, sessions, answers, mapper);
        var summary = new PreferenceSummaryService(sessions, answers, service, mapper);
        UUID id = UUID.randomUUID(), definitionId = UUID.randomUUID();
        var now = LocalDateTime.now();
        var session = new TestSession(id.toString(), "fixture-member", definitionId.toString(), "MENS", "[\"Q09\"]", now, now.plusMinutes(2));
        when(sessions.findByIdAndMemberIdForUpdate(id.toString(), "fixture-member")).thenReturn(Optional.of(session));
        when(sessions.findBySessionIdAndMallMemberId(id.toString(), "fixture-member")).thenReturn(Optional.of(session));
        var definition = mock(TestDefinition.class);
        when(definition.getTestMode()).thenReturn("SIMPLE");
        when(definitions.findById(definitionId.toString())).thenReturn(Optional.of(definition));
        var question = mock(QuestionDefinition.class);
        when(question.getQuestionPayload()).thenReturn(RankedComparisonTest.definition());
        when(questions.findById(new QuestionDefinitionId(definitionId.toString(), "Q09"))).thenReturn(Optional.of(question));
        var payload = mapper.readTree(RankedComparisonTest.answer("[\"B\",\"A\"]"));
        service.saveAnswer("fixture-member", id, new SaveAnswerRequest("Q09", 0L, payload));
        var captor = org.mockito.ArgumentCaptor.forClass(SessionAnswer.class);
        verify(answers).save(captor.capture());
        var stored = captor.getValue();
        assertEquals(payload, mapper.readTree(stored.getAnswerPayload()));
        assertEquals(RankedComparisonTest.definition(), stored.getPresentedPayload());
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc(id.toString())).thenReturn(List.of(stored));
        when(answers.findById(new SessionAnswerId(id.toString(), "Q09"))).thenReturn(Optional.of(stored));
        var snapshot = summary.snapshot("fixture-member", id);
        assertEquals(payload, snapshot.answers().get("Q09"));
        assertEquals(1, snapshot.session().revision());

        var invalid = mapper.readTree(RankedComparisonTest.answer("[\"A\",\"A\"]"));
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.saveAnswer("fixture-member", id, new SaveAnswerRequest("Q09", 1L, invalid))).getStatusCode().value());
        assertEquals(1, session.getRevision());
        assertEquals(payload, mapper.readTree(stored.getAnswerPayload()));

        var updated = mapper.readTree(RankedComparisonTest.answer("[\"A\",\"B\"]"));
        service.saveAnswer("fixture-member", id, new SaveAnswerRequest("Q09", 1L, updated));
        assertEquals(updated, summary.snapshot("fixture-member", id).answers().get("Q09"));
        assertEquals(2, stored.getAnswerRevision());
        verify(answers, times(1)).save(any());
    }
}
