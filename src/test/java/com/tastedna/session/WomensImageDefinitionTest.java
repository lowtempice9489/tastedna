package com.tastedna.session;

import com.tastedna.scoring.application.*;
import com.tastedna.scoring.domain.OptionScoringRule;
import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import com.tastedna.session.domain.*;
import com.tastedna.session.persistence.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WomensImageDefinitionTest {
    static final String ID = "6acc799d-1348-4f34-b756-593e9f5bfd52";
    static final JsonMapper MAPPER = JsonMapper.builder().build();
    static List<String> sql() throws Exception {
        return Files.readAllLines(Path.of("src/main/resources/db/migration/V18__add_womens_simple_q05_q07.sql"));
    }
    static Map<String, JsonNode> questions() throws Exception {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (String line : sql()) if (line.startsWith("INSERT INTO td_question_definition")) {
            String qid = "Q%02d".formatted(result.size() + 5);
            result.put(qid, MAPPER.readTree(line.substring(line.indexOf(",'{") + 2, line.lastIndexOf("','{}',TRUE)"))));
        }
        return result;
    }
    static JsonNode observation(String qid, String option) throws Exception {
        String line = sql().stream().filter(s -> s.startsWith("INSERT INTO td_option_scoring_rule")
                && s.contains("'" + qid + "','" + option + "'")).findFirst().orElseThrow();
        return MAPPER.readTree(line.substring(line.lastIndexOf("'{") + 1, line.lastIndexOf("');")));
    }
    JsonNode answer(String... ids) { return MAPPER.valueToTree(Map.of("selectedOptionIds", ids)); }

    @Test void definitionsReuseFixtureImagesAndOnlyAddExclusiveNeutralAnswer() throws Exception {
        var questions = questions();
        assertEquals(Set.of("Q05", "Q06", "Q07"), questions.keySet());
        for (var entry : questions.entrySet()) {
            var fixture = MAPPER.readTree(Files.readString(Path.of("dev/fixtures/simple/womens_simple_" + entry.getKey().toLowerCase() + "_draft.json")));
            var expected = fixture.path("questionPayload").path("options");
            var options = entry.getValue().path("options");
            assertEquals(expected.size() + 1, options.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).path("id"), options.get(i).path("id"));
                assertEquals(expected.get(i).path("images"), options.get(i).path("images"));
                for (var image : options.get(i).path("images"))
                    assertTrue(Files.isRegularFile(Path.of(image.asString().substring("/assets/".length()))));
            }
            assertEquals("NONE", options.get(options.size() - 1).path("id").asString());
            assertEquals(3, entry.getValue().path("maxSelections").asInt());
        }
        assertTrue(sql().stream().filter(s -> s.startsWith("INSERT")).allMatch(s -> s.contains(ID)));
        assertTrue(sql().stream().noneMatch(s -> s.startsWith("UPDATE") || s.startsWith("DELETE")));
    }
    @Test void requiresResponseAllowsUpToThreeOrNoneAndRejectsConflictsDuplicatesUnknowns() throws Exception {
        for (var q : questions().values()) {
            for (var valid : List.of(answer("A"), answer("A", "B", "C"), answer("NONE")))
                assertDoesNotThrow(() -> MultiChoiceAnswerValidator.validate(q.toString(), valid, MAPPER));
            for (var invalid : List.of(answer(), answer("A", "B", "C", "D"), answer("A", "A"), answer("UNKNOWN"), answer("NONE", "A")))
                assertThrows(IllegalArgumentException.class, () -> MultiChoiceAnswerValidator.validate(q.toString(), invalid, MAPPER));
        }
    }
    @Test void rawPipelineKeepsNeutralAnswersOutOfPositiveAndAvoidanceObservations() throws Exception {
        var sessions = mock(TestSessionRepository.class);
        var answers = mock(SessionAnswerRepository.class);
        var resolver = mock(SelectedOptionRuleResolver.class);
        var session = mock(TestSession.class);
        when(session.getDefinitionId()).thenReturn(ID);
        when(session.getQuestionOrder()).thenReturn("[\"Q05\",\"Q06\",\"Q07\"]");
        when(sessions.findBySessionIdAndMallMemberId("test", "member")).thenReturn(Optional.of(session));
        var service = new SessionObservationService(answers, sessions, new DirectObservationCollector(resolver), MAPPER);
        for (String selected : List.of("NONE", "A")) {
            List<SessionAnswer> rows = new ArrayList<>();
            for (var entry : questions().entrySet()) {
                var rule = mock(OptionScoringRule.class);
                when(rule.getObservationPayload()).thenReturn(observation(entry.getKey(), selected).toString());
                when(resolver.resolve(ID, entry.getKey(), List.of(selected))).thenReturn(Map.of(selected, rule));
                rows.add(new SessionAnswer("test", entry.getKey(), answer(selected).toString(), entry.getValue().toString(), 1, LocalDateTime.now()));
            }
            when(answers.findByIdSessionIdOrderByIdQuestionIdAsc("test")).thenReturn(rows);
            var raw = service.collectRaw("member", "test");
            assertEquals(selected.equals("NONE") ? Set.of("PREFERENCE_UNDETERMINED", "NO_EXPLICIT_OUTFIT_AVOIDANCE")
                    : Set.of("DIRECT_OUTFIT_PREFERENCE", "DIRECT_CLOTHING_PREFERENCE", "DIRECT_OUTFIT_AVOIDANCE"), raw.keySet());
            assertEquals(3, raw.values().stream().mapToInt(List::size).sum());
        }
    }
}
