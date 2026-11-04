package com.tastedna.scoring;

import com.tastedna.scoring.application.*;
import com.tastedna.session.RankedComparisonTest;
import com.tastedna.session.domain.*;
import com.tastedna.session.persistence.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankedObservationTest {
    private final SessionAnswerRepository answers = mock(SessionAnswerRepository.class);
    private final TestSessionRepository sessions = mock(TestSessionRepository.class);
    private final DirectObservationCollector collector = mock(DirectObservationCollector.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SessionObservationService service = new SessionObservationService(answers, sessions, collector, mapper);

    @Test void rawPreservesRankAndProvenanceWithoutApplyingSingleComparisonRules() throws Exception {
        var session = mock(TestSession.class);
        when(session.getDefinitionId()).thenReturn("fixture-definition");
        when(session.getQuestionOrder()).thenReturn("[\"Q01\",\"Q09\"]");
        when(sessions.findBySessionIdAndMallMemberId("session", "member")).thenReturn(Optional.of(session));
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc("session")).thenReturn(List.of(
                new SessionAnswer("session", "Q01", "{\"selectedOptionIds\":[\"A\"]}",
                        "{\"kind\":\"MULTIPLE_CHOICE\",\"options\":[{\"id\":\"A\"},{\"id\":\"B\"}]}", 1, LocalDateTime.now()),
                new SessionAnswer("session", "Q09", RankedComparisonTest.answer("[\"B\",\"A\"]"),
                        RankedComparisonTest.definition(), 2, LocalDateTime.now())));
        var raw = service.collectRaw("member", "session");
        var ranked = raw.get("RANKED_COMPARISON_PREFERENCE");
        assertEquals(List.of("C02:B", "C02:A"), ranked.stream().map(RawObservationAggregator.RawObservation::optionId).toList());
        assertEquals(List.of("1", "2"), ranked.stream().map(RawObservationAggregator.RawObservation::observedValue).toList());
        var negative = raw.get("RANKED_COMPARISON_NON_PREFERENCE");
        assertEquals(1, negative.size());
        assertEquals("C02:C", negative.getFirst().optionId());
        assertEquals("UNSELECTED_IN_RANKED_GROUP", negative.getFirst().evidence());
        for (var values : raw.values()) for (var observation : values) {
            assertEquals("fixture-definition", observation.definitionId());
            assertEquals("Q09", observation.questionId());
            assertEquals(2, observation.answerRevision());
            assertNotEquals("EXPLICIT_SELECTION", observation.evidence());
        }
        verify(collector).collectParsed("fixture-definition", "Q01", List.of("A"), mapper);
        verify(collector).collectParsed("fixture-definition", "Q09", List.of("C01:A"), mapper);
        verifyNoMoreInteractions(collector);
    }

    @Test void standaloneEmptyRankedGroupNeedsNoScoringRuleAndRetainsAllNonPreferences() throws Exception {
        var session = mock(TestSession.class);
        when(session.getDefinitionId()).thenReturn("fixture-definition");
        when(session.getQuestionOrder()).thenReturn("[\"Q09\"]");
        when(sessions.findBySessionIdAndMallMemberId("session", "member")).thenReturn(Optional.of(session));
        var definition = (tools.jackson.databind.node.ObjectNode) mapper.readTree(RankedComparisonTest.definition());
        ((tools.jackson.databind.node.ArrayNode) definition.path("comparisons")).remove(0);
        ((tools.jackson.databind.node.ObjectNode) definition.path("comparisons").get(0)).put("minSelections", 0);
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc("session")).thenReturn(List.of(new SessionAnswer("session", "Q09",
                "{\"selections\":[{\"comparisonId\":\"C02\",\"selectedOptionIds\":[]}]}", definition.toString(), 1, LocalDateTime.now())));
        var raw = service.collectRaw("member", "session");
        assertFalse(raw.containsKey("RANKED_COMPARISON_PREFERENCE"));
        assertEquals(3, raw.get("RANKED_COMPARISON_NON_PREFERENCE").size());
        verifyNoInteractions(collector);
    }
}
