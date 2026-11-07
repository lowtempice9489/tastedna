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
    final SimpleInitialCandidateService service = new SimpleInitialCandidateService(sessions, answers, questions, core, catalog, mapper, observations);
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
        assertNull(result.simpleRepresentative());
        assertEquals("DEFERRED", result.decision().status());
        assertTrue(result.decision().results().isEmpty());
        assertTrue(result.decision().missingRequirements().contains("CANDIDATE_DISCRIMINATION_RULE"));
        assertTrue(result.decision().missingRequirements().contains("FINALIZATION_RULE"));
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
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, ApparelScope.womens, "SUBMITTED", 10, futureDefinition, null));
        select(futureDefinition, "R02");
        assertEquals("R02", service.find("member", id).simpleRepresentative().candidateId());
        assertEquals("DEFERRED", service.find("member", id).decision().status());
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
        var realService = new SimpleInitialCandidateService(sessions, answers, questions, realCore, catalog, mapper, raw);
        var response = realService.find("member", id);
        assertEquals(List.of("R01", "R03"), response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertEquals("미니멀", response.candidates().getFirst().sources().getFirst().optionLabel());

        // 저장 답변을 수정하면 이전 선택을 재사용하지 않고 최신 관측만 후보로 연결한다.
        var revised = new SessionAnswer(id.toString(), "Q02",
                mapper.writeValueAsString(Map.of("selectedOptionIds", List.of("R02"))),
                question.toString(), 3, LocalDateTime.now());
        when(answers.findById(new SessionAnswerId(id.toString(), "Q02"))).thenReturn(Optional.of(revised));
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc(id.toString())).thenReturn(List.of(revised));
        when(resolver.resolve(definition.toString(), "Q02", List.of("R02")))
                .thenReturn(Map.of("R02", rules.get("R02")));
        var updated = realService.find("member", id);
        assertEquals(List.of("R02"), updated.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertEquals(3, updated.simpleRepresentative().sources().getFirst().answerRevision());
        assertTrue(updated.candidates().getFirst().evidence().unresolved().isEmpty());
        assertEquals("DEFERRED", updated.decision().status());
        assertFalse(updated.finalResult());
        assertTrue(updated.decision().results().isEmpty());
        assertEquals(updated, realService.find("member", id));
    }
    @Test void excludesUnknownMismatchedAndIndirectEvidenceAndMissingCatalog() {
        select(definition, "UNKNOWN");
        assertTrue(service.find("member", id).candidates().isEmpty());
        assertNull(service.find("member", id).simpleRepresentative());
        assertTrue(service.find("member", id).decision().missingRequirements().contains("MAPPED_DIRECT_PREFERENCE_EVIDENCE"));
        for (String evidence : List.of("EXPLICIT_SELECTION", "INFERRED")) {
            when(observations.collectRaw("member", id.toString())).thenReturn(Map.of("DIRECT_FASHION_LOOK_PREFERENCE", List.of(
                    new RawObservationAggregator.RawObservation(definition.toString(), "DIRECT_FASHION_LOOK_PREFERENCE",
                            evidence.equals("INFERRED") ? label("R01") : "different meaning", evidence, "Q02", "R01", 2))));
            assertTrue(service.find("member", id).candidates().isEmpty());
        assertNull(service.find("member", id).simpleRepresentative());
        }
        select(definition, "R01");
        when(catalog.findByCandidateIdIn(any())).thenReturn(List.of());
        assertTrue(service.find("member", id).candidates().isEmpty());
        assertNull(service.find("member", id).simpleRepresentative());
    }
    @Test void neverInventsPriorityToTruncateMoreThanFiveCandidates() {
        select(definition, "R01", "R02", "R03", "R04", "R05");
        assertEquals(5, service.find("member", id).candidates().size());
        assertTrue(service.find("member", id).decision().results().isEmpty());
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
    @Test void additionalFactsRemainUnresolvedWithoutPromotingExperimentalMappings() {
        select(definition, "R01");
        var facts = new ArrayList<RawObservationAggregator.RawObservation>();
        facts.add(fact("Q02", "R01", "DIRECT_FASHION_LOOK_PREFERENCE", "미니멀", "EXPLICIT_SELECTION"));
        facts.add(fact("Q01", "A", "DIRECT_COLOR_PREFERENCE", "블랙", "EXPLICIT_SELECTION"));
        facts.add(fact("Q03", "S02", "DIRECT_SILHOUETTE_PREFERENCE", "레귤러", "EXPLICIT_SELECTION"));
        facts.add(fact("Q04", "P01", "DIRECT_PATTERN_GRAPHIC_PREFERENCE", "무지", "EXPLICIT_SELECTION"));
        facts.add(fact("Q05", "A", "DIRECT_CLOTHING_PREFERENCE", "상의 A", "EXPLICIT_SELECTION"));
        facts.add(fact("Q06", "B", "DIRECT_OUTFIT_PREFERENCE", "착장 B", "EXPLICIT_SELECTION"));
        facts.add(fact("Q07", "C", "DIRECT_OUTFIT_AVOIDANCE", "착장 C", "EXPLICIT_SELECTION"));
        facts.add(fact("Q08", "C01:A", "DIRECT_COMPARISON_PREFERENCE", "비교 A", "EXPLICIT_SELECTION"));
        facts.add(fact("Q09", "C02:A", "RANKED_COMPARISON_PREFERENCE", "2", "EXPLICIT_RANKED_SELECTION"));
        facts.add(fact("Q09", "C02:B", "RANKED_COMPARISON_PREFERENCE", "1", "EXPLICIT_RANKED_SELECTION"));
        facts.add(fact("Q09", "C02:C", "RANKED_COMPARISON_NON_PREFERENCE", "NOT_PREFERRED", "UNSELECTED_IN_RANKED_GROUP"));
        facts.add(fact("Q09", "C01:A", "DIRECT_COMBINATION_PREFERENCE", "A · 절제된 코디 그룹", "EXPLICIT_SELECTION"));
        facts.add(fact("Q10", "D", "DIRECT_FINAL_OUTFIT_PREFERENCE", "착장 D", "EXPLICIT_SELECTION"));
        facts.add(facts.get(6));
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        var result = service.find("member", id);
        var evidence = result.candidates().getFirst().evidence();
        assertEquals("DEFERRED", result.decision().status());
        assertTrue(result.decision().results().isEmpty());
        assertEquals(result.candidates().getFirst().sources(), evidence.positive());
        assertTrue(evidence.avoidanceOrConflict().isEmpty());
        assertEquals(12, evidence.unresolved().size());
        assertEquals("C01", evidence.unresolved().stream().filter(o -> o.observationType().equals("DIRECT_COMBINATION_PREFERENCE")).findFirst().orElseThrow().comparisonId());
        assertEquals(1, evidence.unresolved().stream().filter(o -> o.observationType().equals("DIRECT_OUTFIT_AVOIDANCE")).count());
        var ranked = evidence.unresolved().stream().filter(o -> o.rank() != null).toList();
        assertEquals(List.of("C02:B", "C02:A"), ranked.stream().map(SimpleInitialCandidateService.Observation::optionId).toList());
        assertEquals(List.of(1, 2), ranked.stream().map(SimpleInitialCandidateService.Observation::rank).toList());
        assertEquals("C02", ranked.getFirst().comparisonId());
        assertEquals(1, evidence.unresolved().stream().filter(o -> o.evidenceType().equals("UNSELECTED_IN_RANKED_GROUP")).count());
        Collections.reverse(facts);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        assertEquals(result, service.find("member", id));
    }
    @Test void noAvoidanceAndUnselectedOrdinaryOptionsNeverBecomeCandidateConflict() {
        select(definition, "R01");
        var facts = List.of(fact("Q02", "R01", "DIRECT_FASHION_LOOK_PREFERENCE", "미니멀", "EXPLICIT_SELECTION"),
                fact("Q07", "NONE", "NO_EXPLICIT_OUTFIT_AVOIDANCE", "없음", "EXPLICIT_SELECTION"));
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        var evidence = service.find("member", id).candidates().getFirst().evidence();
        assertTrue(evidence.avoidanceOrConflict().isEmpty());
        assertEquals(1, evidence.unresolved().size());
        assertEquals("NO_EXPLICIT_OUTFIT_AVOIDANCE", evidence.unresolved().getFirst().observationType());
    }
    RawObservationAggregator.RawObservation fact(String q, String option, String type, String value, String evidence) {
        return new RawObservationAggregator.RawObservation(definition.toString(), type, value, evidence, q, option, 2);
    }
    Map<String, List<RawObservationAggregator.RawObservation>> group(List<RawObservationAggregator.RawObservation> facts) {
        return facts.stream().collect(java.util.stream.Collectors.groupingBy(RawObservationAggregator.RawObservation::type));
    }
}
