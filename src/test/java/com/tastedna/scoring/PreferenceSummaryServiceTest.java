package com.tastedna.scoring;

import com.tastedna.scoring.application.PreferenceSummaryService;
import com.tastedna.session.ImageDefinitionTest;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.domain.*;
import com.tastedna.session.persistence.*;
import com.tastedna.session.api.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreferenceSummaryServiceTest {
    private final TestSessionRepository sessions = mock(TestSessionRepository.class);
    private final SessionAnswerRepository answers = mock(SessionAnswerRepository.class);
    private final TestSessionService lifecycle = mock(TestSessionService.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PreferenceSummaryService service = new PreferenceSummaryService(sessions, answers, lifecycle, mapper);
    private final UUID id = UUID.randomUUID();

    @Test void deniesAnotherMembersResultBeforeReadingAnswers() {
        when(sessions.findByIdAndMemberIdForUpdate(id.toString(), "other")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.summarize("other", id)).getStatusCode().value());
        verifyNoInteractions(answers);
    }
    @Test void requiresSubmission() {
        var session = mock(TestSession.class);
        when(session.getStatus()).thenReturn("IN_PROGRESS");
        when(sessions.findByIdAndMemberIdForUpdate(id.toString(), "member")).thenReturn(Optional.of(session));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.summarize("member", id)).getStatusCode().value());
        verifyNoInteractions(answers);
    }
    @Test void preservesAllSelectionsComparisonsAndExplicitAvoidanceWithoutInventingClassification() throws Exception {
        prepare("G");
        var summary = service.summarize("member", id);
        assertFalse(summary.comprehensiveResult());
        assertEquals("미니멀", summary.title());
        assertEquals(14, summary.evidence().size());
        var avoid = summary.evidence().stream().filter(e -> e.questionId().equals("Q07")).findFirst().orElseThrow();
        assertEquals("EXPLICIT_AVOIDANCE", avoid.meaning());
        assertEquals("G", avoid.selections().getFirst().optionId());
        assertEquals(7, avoid.answerRevision());
        var compare = summary.evidence().stream().filter(e -> "C02".equals(e.comparisonId()) && e.questionId().equals("Q09")).findFirst().orElseThrow();
        assertEquals("E", compare.selections().getFirst().optionId());
        assertEquals(4, compare.alternatives().size());
        assertEquals(10, summary.session().revision());
    }
    @Test void noneIsNotAnAvoidanceAndMissingAnswersCannotProduceResult() throws Exception {
        prepare("NONE");
        var summary = service.summarize("member", id);
        assertEquals("NO_EXPLICIT_AVOIDANCE", summary.evidence().stream().filter(e -> e.questionId().equals("Q07")).findFirst().orElseThrow().meaning());
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc(id.toString())).thenReturn(List.of());
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.summarize("member", id)).getStatusCode().value());
    }
    private void prepare(String avoidance) throws Exception {
        var session = mock(TestSession.class);
        when(session.getStatus()).thenReturn("SUBMITTED");
        when(sessions.findByIdAndMemberIdForUpdate(id.toString(), "member")).thenReturn(Optional.of(session));
        when(lifecycle.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, ApparelScope.mens, "SUBMITTED", 10,
                UUID.fromString("33333333-3333-4333-8333-333333333333"), Instant.now()));
        List<SessionAnswer> rows = new ArrayList<>();
        var questions = ImageDefinitionTest.questions();
        String[] payloads = {
          "{\"selectedOptionIds\":[\"A\"]}", "{\"selectedOptionIds\":[\"R01\"]}",
          "{\"selectedOptionIds\":[\"S02\"]}", "{\"selectedOptionIds\":[\"P01\"]}",
          "{\"selectedOptionId\":\"A\"}", "{\"selectedOptionId\":\"G\"}",
          "{\"selectedOptionIds\":[\""+avoidance+"\"]}",
          "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"A\"},{\"comparisonId\":\"C02\",\"selectedOptionId\":\"A\"},{\"comparisonId\":\"C03\",\"selectedOptionId\":\"B\"}]}",
          "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"B\"},{\"comparisonId\":\"C02\",\"selectedOptionId\":\"E\"},{\"comparisonId\":\"C03\",\"selectedOptionId\":\"A\"}]}",
          "{\"selectedOptionId\":\"J\"}"};
        for (int i=0;i<10;i++) {
            String qid = "Q%02d".formatted(i+1);
            rows.add(new SessionAnswer(id.toString(), qid, payloads[i], questions.get(qid).toString(), i+1, LocalDateTime.now()));
        }
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc(id.toString())).thenReturn(rows);
    }
}

