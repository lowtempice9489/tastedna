package com.tastedna.scoring;

import com.tastedna.definition.domain.QuestionDefinition;
import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.scoring.application.*;
import com.tastedna.scoring.domain.ResultCandidate;
import com.tastedna.scoring.persistence.StyleCoreDirectMappingRepository;
import com.tastedna.scoring.persistence.ResultCandidateRepository;
import com.tastedna.session.ImageDefinitionTest;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.api.dto.*;
import com.tastedna.session.domain.*;
import com.tastedna.session.persistence.SessionAnswerRepository;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleInitialCandidateServiceTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final TestSessionService sessions = mock(TestSessionService.class);
    final SessionAnswerRepository answers = mock(SessionAnswerRepository.class);
    final QuestionDefinitionRepository questions = mock(QuestionDefinitionRepository.class);
    final SessionObservationService observations = mock(SessionObservationService.class);
    final StyleCoreDirectMappingRepository mappings = mock(StyleCoreDirectMappingRepository.class);
    final ResultCandidateRepository catalog = mock(ResultCandidateRepository.class);
    final StyleCoreCandidateService core = new StyleCoreCandidateService(observations, mapper, mappings, null, null, null, null);
    final SimpleInitialCandidateService service = new SimpleInitialCandidateService(sessions, answers, questions, core, catalog, mapper);
    final UUID id = UUID.randomUUID();
    final UUID definition = UUID.fromString("33333333-3333-4333-8333-333333333333");
    JsonNode question;

    @BeforeEach void setup() throws Exception {
        question = ImageDefinitionTest.questions().get("Q02");
        var approved = mock(QuestionDefinition.class);
        when(approved.getQuestionPayload()).thenReturn(question.toString());
        when(questions.findByIdDefinitionIdAndIdQuestionId(definition.toString(), "Q02")).thenReturn(Optional.of(approved));
        session(definition, "SUBMITTED");
        var rows = new ArrayList<StyleCoreDirectMappingRepository.DirectMapping>();
        // 실제 V9 매핑을 읽어 테스트가 임의의 후보 연결을 만들지 않게 한다.
        var pattern = java.util.regex.Pattern.compile("'Q02', '(R\\d+)', '(R\\d+)'");
        var matcher = pattern.matcher(Files.readString(Path.of("src/main/resources/db/migration/V9__create_style_core_direct_mapping.sql")));
        while (matcher.find()) rows.add(new StyleCoreDirectMappingRepository.DirectMapping(matcher.group(1), matcher.group(2)));
        assertEquals(12, rows.size());
        when(mappings.findByDefinitionAndQuestion("22222222-2222-4222-8222-222222222222", "Q02")).thenReturn(rows);
        var entries = new ArrayList<ResultCandidate>();
        for (var row : rows) {
            var candidate = mock(ResultCandidate.class);
            when(candidate.getCandidateId()).thenReturn(row.candidateId());
            when(candidate.getCandidateName()).thenReturn("Catalog " + row.candidateId());
            when(candidate.isStyleCore()).thenReturn(true);
            entries.add(candidate);
        }
        when(catalog.findByCandidateIdIn(any())).thenReturn(entries);
    }
    void session(UUID def, String status) {
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, ApparelScope.mens, status, 10, def, null));
    }
    String label(String option) {
        for (var item : question.path("options")) if (option.equals(item.path("id").asString())) return item.path("label").asString();
        return "unknown";
    }
    void select(UUID def, String... ids) {
        when(answers.findById(new SessionAnswerId(id.toString(), "Q02"))).thenReturn(Optional.of(new SessionAnswer(id.toString(), "Q02",
                mapper.writeValueAsString(Map.of("selectedOptionIds", ids)), question.toString(), 2, LocalDateTime.now())));
        var raw = Arrays.stream(ids).map(option -> new RawObservationAggregator.RawObservation(def.toString(),
                "DIRECT_FASHION_LOOK_PREFERENCE", label(option), "EXPLICIT_SELECTION", "Q02", option, 2)).toList();
        when(observations.collectRaw("member", id.toString())).thenReturn(Map.of("DIRECT_FASHION_LOOK_PREFERENCE", raw));
    }
    @Test void actualImageChoicesKeepIdsLabelsAndRepeatableUnrankedOrder() throws Exception {
        select(definition, "R12", "R03", "R01", "R08");
        var result = service.find("member", id);
        assertEquals(List.of("R01", "R03", "R08", "R12"), result.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertEquals(result, service.find("member", id));
        assertFalse(result.finalResult());
        assertEquals("INITIAL_CANDIDATES", result.status());
        assertEquals(definition, result.definitionId());
        for (var candidate : result.candidates()) {
            var source = candidate.sources().getFirst();
            assertEquals("Q02", source.questionId());
            assertEquals(candidate.candidateId(), source.optionId());
            assertEquals(label(source.optionId()), source.optionLabel());
            assertEquals(question.path("prompt").asString(), source.questionLabel());
            assertEquals(2, source.answerRevision());
        }
        var old = mapper.readTree(Files.readString(Path.of("dev/fixtures/simple/simple_q02_question_payload.json")));
        assertEquals(old.path("options"), question.path("options"));
    }
    @Test void sharedContractWorksWithAnotherDefinitionWithoutGenderAlgorithm() {
        UUID futureDefinition = UUID.randomUUID();
        session(futureDefinition, "SUBMITTED");
        select(futureDefinition, "R02");
        assertEquals("R02", service.find("member", id).candidates().getFirst().candidateId());
    }
    @Test void storedAnswerFlowsThroughRealObservationCollectorAndCandidateExtractor() throws Exception {
        select(definition, "R03", "R01");
        var stored = answers.findById(new SessionAnswerId(id.toString(), "Q02")).orElseThrow();
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc(id.toString())).thenReturn(List.of(stored));
        var sessionRepository = mock(com.tastedna.session.persistence.TestSessionRepository.class);
        var session = mock(TestSession.class);
        when(session.getDefinitionId()).thenReturn(definition.toString());
        when(session.getQuestionOrder()).thenReturn("[\"Q02\"]");
        when(sessionRepository.findBySessionIdAndMallMemberId(id.toString(), "member")).thenReturn(Optional.of(session));
        var resolver = mock(SelectedOptionRuleResolver.class);
        var rules = new HashMap<String, com.tastedna.scoring.domain.OptionScoringRule>();
        for (String line : Files.readAllLines(Path.of("src/main/resources/db/migration/V13__add_mens_image_simple_definition.sql"))) {
            if (!line.startsWith("INSERT INTO td_option_scoring_rule") || !line.contains("'Q02'")) continue;
            String option = line.substring(line.indexOf("'Q02','") + 7, line.indexOf("'Q02','") + 10);
            var rule = mock(com.tastedna.scoring.domain.OptionScoringRule.class);
            when(rule.getObservationPayload()).thenReturn(line.substring(line.lastIndexOf("'{") + 1, line.lastIndexOf("');")));
            rules.put(option, rule);
        }
        when(resolver.resolve(definition.toString(), "Q02", List.of("R03", "R01"))).thenReturn(rules);
        var raw = new SessionObservationService(answers, sessionRepository, new DirectObservationCollector(resolver), mapper);
        var realCore = new StyleCoreCandidateService(raw, mapper, mappings, null, null, null, null);
        var realService = new SimpleInitialCandidateService(sessions, answers, questions, realCore, catalog, mapper);
        var response = realService.find("member", id);
        assertEquals(List.of("R01", "R03"), response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertEquals("미니멀", response.candidates().getFirst().sources().getFirst().optionLabel());
    }
    @Test void excludesUnknownMismatchedAndIndirectEvidenceAndMissingCatalog() {
        select(definition, "UNKNOWN");
        assertTrue(service.find("member", id).candidates().isEmpty());
        for (String evidence : List.of("EXPLICIT_SELECTION", "INFERRED")) {
            when(observations.collectRaw("member", id.toString())).thenReturn(Map.of("DIRECT_FASHION_LOOK_PREFERENCE", List.of(
                    new RawObservationAggregator.RawObservation(definition.toString(), "DIRECT_FASHION_LOOK_PREFERENCE",
                            evidence.equals("INFERRED") ? label("R01") : "different meaning", evidence, "Q02", "R01", 2))));
            assertTrue(service.find("member", id).candidates().isEmpty());
        }
        select(definition, "R01");
        when(catalog.findByCandidateIdIn(any())).thenReturn(List.of());
        assertTrue(service.find("member", id).candidates().isEmpty());
    }
    @Test void neverInventsPriorityToTruncateMoreThanFiveCandidates() {
        select(definition, "R01", "R02", "R03", "R04", "R05");
        assertEquals(5, service.find("member", id).candidates().size());
        select(definition, "R01", "R02", "R03", "R04", "R05", "R06");
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.find("member", id)).getStatusCode().value());
    }
    @Test void rejectsUnsubmittedAndOtherMembersBeforeReadingEvidence() {
        session(definition, "IN_PROGRESS");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.find("member", id)).getStatusCode().value());
        when(sessions.get("other", id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.find("other", id)).getStatusCode().value());
        verifyNoInteractions(answers, observations, catalog);
    }
}
