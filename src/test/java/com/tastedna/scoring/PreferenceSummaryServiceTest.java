package com.tastedna.scoring;

import com.tastedna.scoring.application.PreferenceSummaryService;
import com.tastedna.session.ImageDefinitionTest;
import com.tastedna.session.RankedComparisonTest;
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
    @Test void rankedSummaryKeepsOrderAndSeparatesNonPreferenceFromOrdinaryAlternatives() throws Exception {
        var rows = prepare("NONE");
        rows.set(8, new SessionAnswer(id.toString(), "Q09", RankedComparisonTest.answer("[\"B\",\"A\"]"),
                RankedComparisonTest.definition(), 9, LocalDateTime.now()));
        var summary = service.summarize("member", id);
        var ranked = summary.evidence().stream().filter(e -> e.meaning().equals("RANKED_COMPARATIVE_PREFERENCE")).findFirst().orElseThrow();
        assertEquals(List.of("B", "A"), ranked.selections().stream().map(PreferenceSummaryService.Selection::optionId).toList());
        assertEquals(List.of(1, 2), ranked.selections().stream().map(PreferenceSummaryService.Selection::rank).toList());
        assertEquals(List.of("C"), ranked.notPreferred().stream().map(PreferenceSummaryService.Selection::optionId).toList());
        assertTrue(ranked.alternatives().isEmpty());
        assertEquals(9, ranked.answerRevision());
        for (var other : summary.evidence()) if (other != ranked) {
            assertTrue(other.notPreferred().isEmpty());
            assertTrue(other.selections().stream().allMatch(s -> s.rank() == null));
            var json = mapper.valueToTree(other);
            assertFalse(json.has("notPreferred"));
            assertFalse(json.path("selections").get(0).has("rank"));
        }
        var snapshot = service.snapshot("member", id);
        assertEquals(mapper.readTree(RankedComparisonTest.answer("[\"B\",\"A\"]")), snapshot.answers().get("Q09"));
    }
    @Test void allRankedSelectedProducesNoNegativeEvidence() throws Exception {
        var rows = prepare("NONE");
        rows.set(8, new SessionAnswer(id.toString(), "Q09", RankedComparisonTest.answer("[\"C\",\"B\",\"A\"]"),
                RankedComparisonTest.definition(), 9, LocalDateTime.now()));
        assertTrue(service.summarize("member", id).evidence().stream().allMatch(e -> e.notPreferred().isEmpty()));
    }
    private List<SessionAnswer> prepare(String avoidance) throws Exception {
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
        return rows;
    }
}

