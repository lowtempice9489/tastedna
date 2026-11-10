package com.tastedna.scoring;

import com.tastedna.definition.domain.QuestionDefinition;
import com.tastedna.definition.domain.QuestionDefinitionId;
import com.tastedna.definition.domain.TestDefinition;
import com.tastedna.definition.persistence.TestDefinitionRepository;
import com.tastedna.demo.DemoProduct;
import com.tastedna.demo.DemoProductRepository;
import com.tastedna.demo.DemoProductMatchService;
import com.tastedna.session.application.GuestSimpleService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.tastedna.scoring.domain.OptionScoringRule;
import com.tastedna.session.persistence.TestSessionRepository;
import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import com.tastedna.session.application.single.SingleChoiceAnswerValidator;
import com.tastedna.session.application.comparison.ComparisonAnswerValidator;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
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
        assertTrue(result.decision().missingRequirements().contains("CANDIDATE_SUFFICIENCY_RULE_REQUIRED"));

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
        assertFalse(service.find("member", id).decision().missingRequirements().contains("CANDIDATE_LIMIT_POLICY_REQUIRED"));
        select(definition, "R01", "R02", "R03", "R04", "R05", "R06");
        assertEquals(6, service.find("member", id).candidates().size());
        assertTrue(service.find("member", id).decision().missingRequirements().contains("CANDIDATE_SUFFICIENCY_RULE_REQUIRED"));
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
    @Test void mensBlackComparisonAddsReviewCandidateAndPreservesDirectRepresentative() throws Exception {
        for (String candidate : List.of("R01", "R07", "R03")) {
            for (String option : List.of("A", "B")) {
                select(definition, candidate);
                var support = blackComparison(option, 3);
                var direct = fact("Q02", candidate, "DIRECT_FASHION_LOOK_PREFERENCE", label(candidate), "EXPLICIT_SELECTION");
                when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(direct, support, support)));
                var response = service.find("member", id);
                boolean matches = candidate.equals("R01") && option.equals("A") || candidate.equals("R07") && option.equals("B");
                String supportedId = option.equals("A") ? "R01" : "R07";
                assertEquals(new TreeSet<>(List.of(candidate, supportedId)), new TreeSet<>(response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList()));
                var result = response.candidates().stream().filter(c -> c.candidateId().equals(candidate)).findFirst().orElseThrow();
                assertEquals(1, result.sources().size());
                assertEquals("Q02", result.sources().getFirst().questionId());
                assertEquals(matches ? 2 : 1, result.evidence().positive().size());
                assertEquals(matches ? 0 : 1, result.evidence().unresolved().size());
                if (matches) {
                    var source = result.evidence().positive().getLast();
                    assertEquals("C03:" + option, source.optionId());
                    assertEquals(3, source.answerRevision());
                    assertEquals(support.observedValue(), source.optionLabel());
                }
                assertEquals(result, response.simpleRepresentative());
                var supported = response.candidates().stream().filter(c -> c.candidateId().equals(supportedId)).findFirst().orElseThrow();
                assertEquals(matches ? 1 : 0, supported.sources().size());
                assertEquals(matches ? 2 : 1, supported.evidence().positive().size());
                assertTrue(supported.evidence().unresolved().stream().noneMatch(o -> o.questionId().equals("Q09")));
                assertTrue(response.candidates().stream().allMatch(c -> c.evidence().avoidanceOrConflict().isEmpty()));
                assertFinal(response, supportedId);
                assertEquals(response, service.find("member", id));
            }
        }
    }

    @Test void comparisonCanEnterWithoutQ02AndOldSelectionDoesNotKeepCandidate() throws Exception {
        when(answers.findById(new SessionAnswerId(id.toString(), "Q02"))).thenReturn(Optional.empty());
        var old = blackComparison("A", 3);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(old)));
        var first = service.find("member", id);
        assertEquals(List.of("R01"), first.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertNull(first.simpleRepresentative());
        assertTrue(first.candidates().getFirst().sources().isEmpty());
        var current = blackComparison("B", 4);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(old, current)));
        var updated = service.find("member", id);
        assertEquals(List.of("R07"), updated.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertEquals(4, updated.candidates().getFirst().evidence().positive().getFirst().answerRevision());
        assertNull(updated.simpleRepresentative());
        assertFinal(updated, "R07");
    }

    @Test void unmappedCatalogEntriesAreNotCandidatesAndCombinedOverflowIsPreserved() throws Exception {
        var extra = mock(ResultCandidate.class);
        when(extra.getCandidateId()).thenReturn("R18");
        var entries = new ArrayList<>(catalog.findByCandidateIdIn(List.of()));
        entries.add(extra);
        when(catalog.findByCandidateIdIn(any())).thenReturn(entries);
        select(definition, "R02", "R03", "R04", "R05");
        var raw = new ArrayList<>(observations.collectRaw("member", id.toString()).get("DIRECT_FASHION_LOOK_PREFERENCE"));
        raw.add(blackComparison("B", 3));
        when(observations.collectRaw("member", id.toString())).thenReturn(group(raw));
        var response = service.find("member", id);
        assertEquals(List.of("R02", "R03", "R04", "R05", "R07"), response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertFinal(response, "R07");
        assertNull(response.simpleRepresentative());
        select(definition, "R02", "R03", "R04", "R05", "R06");
        raw.add(fact("Q02", "R06", "DIRECT_FASHION_LOOK_PREFERENCE", label("R06"), "EXPLICIT_SELECTION"));
        when(observations.collectRaw("member", id.toString())).thenReturn(group(raw));
        var expanded = service.find("member", id);
        assertEquals(List.of("R02", "R03", "R04", "R05", "R06", "R07"), expanded.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertFinal(expanded, "R07");
    }

    @Test void changedSelectionRejectsOldRevisionAndWomensComparisonIsNotMapped() throws Exception {
        select(definition, "R01", "R07");
        var direct = List.of(fact("Q02", "R01", "DIRECT_FASHION_LOOK_PREFERENCE", label("R01"), "EXPLICIT_SELECTION"),
                fact("Q02", "R07", "DIRECT_FASHION_LOOK_PREFERENCE", label("R07"), "EXPLICIT_SELECTION"));
        var old = blackComparison("A", 3);
        var facts = new ArrayList<>(direct);
        facts.add(old);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        assertEquals(2, service.find("member", id).candidates().getFirst().evidence().positive().size());
        var current = blackComparison("B", 4);
        facts.add(current);
        // 이전 관측이 섞여도 저장된 최신 선택과 revision만 보조 근거로 인정한다.
        facts.add(new RawObservationAggregator.RawObservation(definition.toString(), current.type(), current.observedValue(),
                current.evidence(), "Q09", "C03:B", 3));
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        var changed = service.find("member", id);
        assertEquals(1, changed.candidates().getFirst().evidence().positive().size());
        assertEquals(2, changed.candidates().getLast().evidence().positive().size());
        assertEquals(4, changed.candidates().getLast().evidence().positive().getLast().answerRevision());
        assertNull(changed.simpleRepresentative());
        assertFinal(changed, "R07");

        UUID womens = UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, ApparelScope.womens, "SUBMITTED", 10, womens, null));
        select(womens, "R03");
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(
                new RawObservationAggregator.RawObservation(womens.toString(), "DIRECT_FASHION_LOOK_PREFERENCE", label("R03"), "EXPLICIT_SELECTION", "Q02", "R03", 2),
                new RawObservationAggregator.RawObservation(womens.toString(), current.type(), current.observedValue(), current.evidence(), "Q09", "C03:B", 4))));
        var response = service.find("member", id);
        assertEquals(List.of("R03"), response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertEquals(1, response.candidates().getFirst().evidence().positive().size());
        assertEquals(1, response.candidates().getFirst().evidence().unresolved().size());
        assertEquals("DEFERRED", response.decision().status());
        assertFalse(response.finalResult());
    }

    RawObservationAggregator.RawObservation blackComparison(String optionId, long revision) throws Exception {
        var q09 = ImageDefinitionTest.questions().get("Q09");
        when(answers.findById(new SessionAnswerId(id.toString(), "Q09"))).thenReturn(Optional.of(new SessionAnswer(
                id.toString(), "Q09", mapper.writeValueAsString(Map.of("selections", List.of(
                Map.of("comparisonId", "C01", "selectedOptionId", "A"),
                Map.of("comparisonId", "C02", "selectedOptionId", "A"),
                Map.of("comparisonId", "C03", "selectedOptionId", optionId)))), q09.toString(), revision, LocalDateTime.now())));
        for (var comparison : q09.path("comparisons")) if (comparison.path("id").asString().equals("C03"))
            for (var option : comparison.path("options")) if (option.path("id").asString().equals(optionId))
                return new RawObservationAggregator.RawObservation(definition.toString(), "DIRECT_COMBINATION_PREFERENCE",
                        option.path("label").asString(), "EXPLICIT_SELECTION", "Q09", "C03:" + optionId, revision);
        throw new AssertionError("Missing actual Q09 C03 option");
    }

    @Test void approvedOutfitEntriesPreserveSourcesScopeAndRepresentative() throws Exception {
        for (var rule : List.of(
                List.of("M", "Q10", "D", "R13"), List.of("M", "Q05", "B", "R16"), List.of("M", "Q05", "C", "R17"),
                List.of("W", "Q05", "B", "R13"), List.of("W", "Q10", "G", "R13"),
                List.of("W", "Q06", "B", "R17"), List.of("W", "Q10", "H", "R17"))) {
            reset(answers);
            UUID def = rule.getFirst().equals("M") ? definition : UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
            session(def, "SUBMITTED");
            select(def, "R03");
            addCatalogCandidate(rule.get(3));
            var direct = observations.collectRaw("member", id.toString()).get("DIRECT_FASHION_LOOK_PREFERENCE").getFirst();
            var outfit = outfit(def, rule.get(1), rule.get(2), 3);
            when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(direct, outfit, outfit)));
            var response = service.find("member", id);
            assertEquals(List.of("R03", rule.get(3)), response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
            var candidate = response.candidates().getLast();
            assertTrue(candidate.sources().isEmpty());
            assertEquals(1, candidate.evidence().positive().size());
            assertEquals(3, candidate.evidence().positive().getFirst().answerRevision());
            assertTrue(candidate.evidence().unresolved().stream().noneMatch(o -> o.questionId().equals(rule.get(1))));
            assertTrue(candidate.evidence().avoidanceOrConflict().isEmpty());
            assertEquals("R03", response.simpleRepresentative().candidateId());
            assertFinal(response, rule.get(3));
            assertEquals(response, service.find("member", id));

            // 동일 옵션/Raw라도 다른 정의나 최신 revision이 아니면 진입하지 않는다.
            session(UUID.randomUUID(), "SUBMITTED");
            assertTrue(service.find("member", id).candidates().isEmpty());
            session(def, "SUBMITTED");
            outfit(def, rule.get(1), rule.get(2), 4);
            assertEquals(List.of("R03"), service.find("member", id).candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        }
    }

    @Test void outfitEvidenceMergesAndRejectsChangedSelectionImageAndInferredEvidence() throws Exception {
        UUID womens = UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
        session(womens, "SUBMITTED");
        addCatalogCandidate("R13");
        var first = outfit(womens, "Q05", "B", 3);
        var second = outfit(womens, "Q10", "G", 4);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(first, second)));
        var response = service.find("member", id);
        assertEquals(1, response.candidates().size());
        assertEquals(2, response.candidates().getFirst().evidence().positive().size());
        assertNull(response.simpleRepresentative());
        outfit(womens, "Q05", "A", 5);
        outfit(womens, "Q10", "A", 5);
        assertTrue(service.find("member", id).candidates().isEmpty());
        var current = outfit(womens, "Q05", "B", 6);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(new RawObservationAggregator.RawObservation(
                womens.toString(), current.type(), current.observedValue(), "INFERRED", "Q05", "B", 6))));
        assertTrue(service.find("member", id).candidates().isEmpty());
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(current)));
        var stored = answers.findById(new SessionAnswerId(id.toString(), "Q05")).orElseThrow();
        when(answers.findById(new SessionAnswerId(id.toString(), "Q05"))).thenReturn(Optional.of(new SessionAnswer(
                id.toString(), "Q05", stored.getAnswerPayload(), stored.getPresentedPayload().replace("/Q05/B.png", "/Q05/A.png"), 6, LocalDateTime.now())));
        assertTrue(service.find("member", id).candidates().isEmpty());
    }

    @Test void realSixInitialCandidatesFinalizeOnlySufficientVisualCandidates() throws Exception {
        addCatalogCandidate("R16");
        select(definition, "R02", "R03", "R04", "R05");
        var facts = new ArrayList<>(observations.collectRaw("member", id.toString()).get("DIRECT_FASHION_LOOK_PREFERENCE"));
        facts.add(blackComparison("B", 3));
        facts.add(outfit(definition, "Q05", "B", 4));
        facts.add(facts.getLast());
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        var response = service.find("member", id);
        assertEquals(List.of("R02", "R03", "R04", "R05", "R07", "R16"), response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertFinal(response, "R07", "R16");
        assertNull(response.simpleRepresentative());
        var support = response.candidates().getLast().evidence().positive();
        assertEquals(1, support.size());
        assertEquals("Q05", support.getFirst().questionId());
        assertEquals("B", support.getFirst().optionId());
        assertEquals(4, support.getFirst().answerRevision());
        assertEquals(3, response.candidates().get(4).evidence().positive().getFirst().answerRevision());
        var review = SimpleCandidateDiscrimination.reviewInitialCandidates(response);
        assertEquals(response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList(),
                review.candidates().stream().map(SimpleCandidateDiscrimination.CandidateReview::candidateId).toList());
        assertEquals(List.of("R07", "R16"), review.validatedCandidateIds());
        assertTrue(review.missingRequirements().isEmpty());
        assertEquals(Set.of("Q05"), review.candidates().getLast().supportingQuestions());
        Collections.reverse(facts);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        assertEquals(response, service.find("member", id));
    }

    @Test void actualMensThreeAndWomensTwoSufficientCandidatesAreStableAndSeparateFromQ02() throws Exception {
        addCatalogCandidate("R13");
        addCatalogCandidate("R16");
        addCatalogCandidate("R17");
        for (boolean womens : List.of(false, true)) {
            reset(answers);
            UUID def = womens ? UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52") : definition;
            session(def, "SUBMITTED");
            select(def, "R03");
            var facts = new ArrayList<>(observations.collectRaw("member", id.toString()).get("DIRECT_FASHION_LOOK_PREFERENCE"));
            if (womens) {
                facts.add(outfit(def, "Q05", "B", 3));
                facts.add(outfit(def, "Q06", "B", 4));
            } else {
                facts.add(blackComparison("B", 3));
                facts.add(outfit(def, "Q05", "B", 4));
                facts.add(outfit(def, "Q10", "D", 5));
            }
            when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
            var response = service.find("member", id);
            assertFinal(response, womens ? new String[]{"R13", "R17"} : new String[]{"R07", "R13", "R16"});
            assertEquals("R03", response.simpleRepresentative().candidateId());
            Collections.reverse(facts);
            when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
            assertEquals(response, service.find("member", id));
            var json = mapper.readTree(mapper.writeValueAsString(response));
            assertEquals(response.decision().representativeCandidate(), json.path("decision").path("representativeCandidate").asString());
            assertEquals("R03", json.path("simpleRepresentative").path("candidateId").asString());
        }
    }

    @Test void coreVisualRelationsFinalizeWithoutQ02AndRejectStaleImageOrScope() throws Exception {
        UUID womens = UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
        for (var rule : List.of(List.of("M", "Q10", "J", "R03"), List.of("M", "Q10", "A", "R05"),
                List.of("W", "Q10", "I", "R04"), List.of("W", "Q10", "A", "R05"), List.of("W", "Q05", "C", "R09"))) {
            reset(answers);
            UUID def = rule.getFirst().equals("M") ? definition : womens;
            session(def, "SUBMITTED");
            var raw = outfit(def, rule.get(1), rule.get(2), 3);
            when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(raw, raw)));
            var response = service.find("member", id);
            assertFinal(response, rule.get(3));
            assertNull(response.simpleRepresentative());
            assertTrue(response.candidates().getFirst().sources().isEmpty());
            assertEquals(1, response.candidates().getFirst().evidence().positive().size());
            assertEquals(3, response.candidates().getFirst().evidence().positive().getFirst().answerRevision());
            assertEquals(response, service.find("member", id));
            var stored = answers.findById(new SessionAnswerId(id.toString(), rule.get(1))).orElseThrow();
            var question = mapper.readTree(stored.getPresentedPayload());
            for (var option : question.path("options")) if (option.path("id").asString().equals(rule.get(2)))
                assertTrue(Files.isRegularFile(Path.of(option.path("images").path(0).asString().replace("/assets/", ""))));

            UUID other = def.equals(definition) ? womens : definition;
            session(other, "SUBMITTED");
            when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(
                    new RawObservationAggregator.RawObservation(other.toString(), raw.type(), raw.observedValue(), raw.evidence(), rule.get(1), rule.get(2), 3))));
            assertEquals("DEFERRED", service.find("member", id).decision().status());
            session(def, "SUBMITTED");
            when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(raw)));
            outfit(def, rule.get(1), rule.get(2), 4);
            assertTrue(service.find("member", id).candidates().isEmpty());
            when(answers.findById(new SessionAnswerId(id.toString(), rule.get(1)))).thenReturn(Optional.of(new SessionAnswer(
                    id.toString(), rule.get(1), stored.getAnswerPayload(), stored.getPresentedPayload().replace(".png", "-changed.png"), 3, LocalDateTime.now())));
            assertTrue(service.find("member", id).candidates().isEmpty());
        }
    }

    @Test void womensCoreAndExistingVisualCandidatesRemainEqualAndReproducible() throws Exception {
        UUID def = UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
        session(def, "SUBMITTED");
        select(def, "R02");
        addCatalogCandidate("R13");
        addCatalogCandidate("R17");
        var facts = new ArrayList<>(observations.collectRaw("member", id.toString()).get("DIRECT_FASHION_LOOK_PREFERENCE"));
        facts.add(outfit(def, "Q05", "C", 3));
        for (String option : List.of("A", "I", "G", "H")) facts.add(outfit(def, "Q10", option, 4));
        var stored = answers.findById(new SessionAnswerId(id.toString(), "Q10")).orElseThrow();
        when(answers.findById(new SessionAnswerId(id.toString(), "Q10"))).thenReturn(Optional.of(new SessionAnswer(id.toString(), "Q10",
                "{\"selectedOptionIds\":[\"A\",\"I\",\"G\",\"H\"]}", stored.getPresentedPayload(), 4, LocalDateTime.now())));
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        var result = service.find("member", id);
        assertFinal(result, "R04", "R05", "R09", "R13", "R17");
        assertEquals(6, result.candidates().size());
        assertEquals("R02", result.simpleRepresentative().candidateId());
        Collections.reverse(facts);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        assertEquals(result, service.find("member", id));
        addCatalogCandidate("R15");
        facts.add(rankedRomantic(5, "D", "C"));
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        var six = service.find("member", id);
        assertEquals(List.of("R04", "R05", "R09", "R13", "R15", "R17"),
                SimpleCandidateDiscrimination.reviewInitialCandidates(six).validatedCandidateIds());
        assertEquals(5, six.decision().results().size());
        assertTrue(six.finalResult());
        assertTrue(six.decision().results().stream().anyMatch(c -> c.candidateId().equals(six.decision().representativeCandidate())));
        assertEquals("R02", six.simpleRepresentative().candidateId());
        Collections.reverse(facts);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        assertEquals(six, service.find("member", id));
    }

    @Test void ambiguousCoreVisualsWithoutCorrespondingDirectPreferenceRemainDeferred() throws Exception {
        UUID def = UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
        session(def, "SUBMITTED");
        for (var pair : List.of(List.of("B", "R02"), List.of("E", "R03"), List.of("L", "R08"),
                List.of("M", "R10"), List.of("F", "R11"), List.of("P", "R12"))) {
            reset(answers);
            select(def, "R02");
            var facts = new ArrayList<>(observations.collectRaw("member", id.toString()).get("DIRECT_FASHION_LOOK_PREFERENCE"));
            facts.add(outfit(def, "Q10", pair.getFirst(), 3));
            when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
            var result = service.find("member", id);
            assertFalse(result.finalResult());
            assertEquals("DEFERRED", result.decision().status());
            assertNull(result.decision().representativeCandidate());
            assertEquals(1, result.candidates().size());
        }
    }

    @Test void romanticRankOneFinalizesWithoutQ02AndRetainsRankAndLatestRevision() throws Exception {
        UUID def = UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
        session(def, "SUBMITTED");
        addCatalogCandidate("R15");
        var old = rankedRomantic(3, "D", "B");
        var nonPreference = new RawObservationAggregator.RawObservation(def.toString(), "RANKED_COMPARISON_NON_PREFERENCE",
                "NOT_PREFERRED", "UNSELECTED_IN_RANKED_GROUP", "Q08", "C05:A", 3);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(old, old, nonPreference)));
        var result = service.find("member", id);
        assertFinal(result, "R15");
        assertNull(result.simpleRepresentative());
        var evidence = result.candidates().getFirst().evidence();
        assertEquals(1, evidence.positive().size());
        assertEquals(1, evidence.positive().getFirst().rank());
        assertEquals(3, evidence.positive().getFirst().answerRevision());
        assertEquals("선택 D", evidence.positive().getFirst().optionLabel());
        assertEquals(List.of("C05:A"), evidence.unresolved().stream().map(SimpleInitialCandidateService.Observation::optionId).toList());
        assertTrue(evidence.avoidanceOrConflict().isEmpty());
        assertEquals(result, service.find("member", id));
        var current = rankedRomantic(4, "B", "D");
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(old, current)));
        assertEquals("DEFERRED", service.find("member", id).decision().status());
        rankedRomantic(5, "D");
        assertTrue(service.find("member", id).candidates().isEmpty());
        var latest = rankedRomantic(5, "D");
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(old, latest)));
        assertEquals(5, service.find("member", id).candidates().getFirst().evidence().positive().getFirst().answerRevision());
    }

    @Test void rankedRomanticRejectsWrongImageDefinitionModeAndRawRank() throws Exception {
        UUID def = UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
        session(def, "SUBMITTED");
        addCatalogCandidate("R15");
        var raw = rankedRomantic(3, "D");
        var stored = answers.findById(new SessionAnswerId(id.toString(), "Q08")).orElseThrow();
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(raw)));
        for (String changed : List.of(stored.getPresentedPayload().replace("Q08_C05_D.png", "Q08_C05_C.png"),
                stored.getPresentedPayload().replace("RANKED_MULTI_SELECT", "SINGLE"))) {
            when(answers.findById(new SessionAnswerId(id.toString(), "Q08"))).thenReturn(Optional.of(new SessionAnswer(
                    id.toString(), "Q08", stored.getAnswerPayload(), changed, 3, LocalDateTime.now())));
            assertTrue(service.find("member", id).candidates().isEmpty());
        }
        rankedRomantic(3, "D");
        session(definition, "SUBMITTED");
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(new RawObservationAggregator.RawObservation(
                definition.toString(), raw.type(), raw.observedValue(), raw.evidence(), "Q08", "C05:D", 3))));
        assertTrue(service.find("member", id).candidates().isEmpty());
        session(def, "SUBMITTED");
        for (var invalid : List.of(
                new RawObservationAggregator.RawObservation(def.toString(), raw.type(), "2", raw.evidence(), "Q08", "C05:D", 3),
                new RawObservationAggregator.RawObservation(def.toString(), raw.type(), "1", "EXPLICIT_SELECTION", "Q08", "C05:D", 3),
                new RawObservationAggregator.RawObservation(def.toString(), raw.type(), "1", raw.evidence(), "Q08", "C04:D", 3))) {
            when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(invalid)));
            assertTrue(service.find("member", id).candidates().isEmpty());
        }
        rankedRomantic(4, "B", "C");
        when(observations.collectRaw("member", id.toString())).thenReturn(group(List.of(raw)));
        assertEquals("DEFERRED", service.find("member", id).decision().status());
    }

    @Test void checkedDenimOutfitsWithoutVintagePreferenceDoNotInventVintageCandidates() throws Exception {
        addCatalogCandidate("R14");
        addCatalogCandidate("R19");
        for (boolean womens : List.of(false, true)) {
            reset(answers);
            UUID def = womens ? UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52") : definition;
            session(def, "SUBMITTED");
            select(def, "R03");
            var facts = new ArrayList<>(observations.collectRaw("member", id.toString()).get("DIRECT_FASHION_LOOK_PREFERENCE"));
            facts.add(outfit(def, "Q05", womens ? "E" : "D", 3));
            facts.add(outfit(def, womens ? "Q06" : "Q10", womens ? "F" : "E", 4));
            when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
            var response = service.find("member", id);
            assertEquals(List.of("R03"), response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
            assertEquals("DEFERRED", response.decision().status());
            assertFalse(response.finalResult());
            assertTrue(response.decision().results().isEmpty());
        }
    }

    @ParameterizedTest(name = "{0} {1}: {2}")
    @CsvSource({
        "M,R02,Q02/R02 Q04/P01 Q03/S05", "W,R02,Q02/R02 Q04/P01 Q03/S05",
        "M,R06,Q02/R06 Q05/C", "W,R06,Q02/R06 Q06/B", "W,R06,Q02/R06 Q10/H",
        "M,R08,Q02/R08 Q05/D", "M,R08,Q02/R08 Q10/E", "W,R08,Q02/R08 Q05/E", "W,R08,Q02/R08 Q06/F",
        "M,R10,Q02/R10 Q04/P06 Q03/S06", "W,R10,Q02/R10 Q04/P06 Q03/S06",
        "M,R11,Q02/R11 Q10/G", "W,R11,Q02/R11 Q10/F",
        "M,R12,Q02/R12 Q09/C01:B", "W,R12,Q02/R12 Q10/P",
        "M,R14,Q02/R08 Q04/P03", "W,R14,Q02/R08 Q04/P03",
        "M,R18,Q02/R01 Q02/R07 Q04/P01", "W,R18,Q02/R01 Q02/R07 Q04/P01",
        "M,R19,Q02/R08 Q02/R05 Q05/D", "W,R19,Q02/R08 Q02/R05 Q05/E",
        "M,R20,Q02/R09 Q02/R11 Q04/P06", "W,R20,Q02/R09 Q02/R11 Q04/P06",
        "W,R21,Q01/O Q09/C04:B",
        "M,R22,Q03/S04 Q08/C01:B", "W,R22,Q03/S04 Q08/C01:B",
        "M,R23,Q04/P03 Q09/C02:A", "W,R23,Q04/P03 Q09/C02:A",
        "M,R24,Q02/R08 Q02/R11 Q05/D", "W,R24,Q02/R08 Q02/R11 Q05/E",
        "M,R25,Q02/R03 Q02/R07 Q04/P03", "W,R25,Q02/R03 Q02/R07 Q04/P03"
    })
    void compositeRulesReachFinalFromRealStoredAnswersAndNeedEveryConjunct(String scope, String candidate, String choices) throws Exception {
        var realService = storedScenario(scope, choices);
        var response = realService.find("member", id);
        assertTrue(SimpleCandidateDiscrimination.reviewInitialCandidates(response).validatedCandidateIds().contains(candidate));
        assertTrue(response.decision().results().stream().anyMatch(c -> c.candidateId().equals(candidate)));
        var result = response.candidates().stream().filter(c -> c.candidateId().equals(candidate)).findFirst().orElseThrow();
        assertEquals("SIMPLE_COMPOSITE_PREFERENCE_INFERENCE", result.evidence().status());
        assertEquals(result.evidence().positive().size(), new HashSet<>(result.evidence().positive()).size());
        assertEquals(response, realService.find("member", id));
        for (String omitted : choices.split(" ")) {
            var reduced = new ArrayList<>(List.of(choices.split(" ")));
            reduced.remove(omitted);
            // 같은 질문의 다른 필수 선택은 보존하고, 빠진 단독 선택은 유효한 중립 대조 옵션으로 교체한다.
            String group = omitted.contains(":") ? omitted.substring(0, omitted.indexOf(':')) : omitted.split("/")[0];
            if (reduced.stream().noneMatch(c -> c.startsWith(group + (omitted.contains(":") ? ":" : "/"))))
                reduced.add(neutralChoice(scope, omitted));
            var missing = storedScenario(scope, String.join(" ", reduced)).find("member", id);
            assertFalse(SimpleCandidateDiscrimination.reviewInitialCandidates(missing).validatedCandidateIds().contains(candidate),
                    candidate + " missing " + omitted);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "M,R01,Q09/C03:A", "M,R07,Q09/C03:B", "M,R03,Q10/J", "M,R05,Q10/A",
        "M,R13,Q10/D", "M,R16,Q05/B", "M,R17,Q05/C",
        "W,R04,Q10/I", "W,R05,Q10/A", "W,R09,Q05/C", "W,R13,Q05/B",
        "W,R15,Q08/C05:D", "W,R17,Q06/B"
    })
    void existingTenVisualFinalsStillReachFromActualStoredAnswers(String scope, String candidate, String choices) throws Exception {
        var response = storedScenario(scope, choices).find("member", id);
        assertTrue(response.decision().results().stream().anyMatch(c -> c.candidateId().equals(candidate)));
        assertNotEquals("SIMPLE_COMPOSITE_PREFERENCE_INFERENCE", response.candidates().stream()
                .filter(c -> c.candidateId().equals(candidate)).findFirst().orElseThrow().evidence().status());
    }

    @Test void compositePoolReductionUsesAllSevenCandidatesAndIsRepeatable() throws Exception {
        var real = storedScenario("M", "Q02/R08 Q02/R05 Q02/R11 Q02/R07 Q04/P03 Q05/D Q08/C01:B Q09/C02:A Q09/C03:A Q10/D");
        var response = real.find("member", id);
        assertEquals(List.of("R01", "R08", "R13", "R14", "R19", "R23", "R24"),
                SimpleCandidateDiscrimination.reviewInitialCandidates(response).validatedCandidateIds());
        assertEquals(5, response.decision().results().size());
        assertTrue(response.candidates().size() > 5);
        assertNull(response.simpleRepresentative());
        Collections.reverse(scenarioAnswers);
        assertEquals(response, real.find("member", id));
        assertTrue(response.decision().results().stream().anyMatch(c -> c.candidateId().equals(response.decision().representativeCandidate())));
    }

    @Test void compositeStoredRevisionImageAndRankBoundaries() throws Exception {
        var real = storedScenario("W", "Q04/P03 Q09/C02:A");
        assertTrue(SimpleCandidateDiscrimination.reviewInitialCandidates(real.find("member", id)).validatedCandidateIds().contains("R23"));
        var answer = answers.findById(new SessionAnswerId(id.toString(), "Q09")).orElseThrow();
        // 저장 답변과 이전 Raw revision을 분리해 오래된 근거의 재사용을 막는다.
        when(answers.findById(answer.getId())).thenReturn(Optional.of(new SessionAnswer(id.toString(), "Q09",
                answer.getAnswerPayload(), answer.getPresentedPayload(), 99, LocalDateTime.now())));
        assertFalse(SimpleCandidateDiscrimination.reviewInitialCandidates(real.find("member", id)).validatedCandidateIds().contains("R23"));
        when(answers.findById(answer.getId())).thenReturn(Optional.of(new SessionAnswer(id.toString(), "Q09",
                answer.getAnswerPayload(), answer.getPresentedPayload().replace("/Q09/C02/A.png", "/Q09/C02/B.png"),
                answer.getAnswerRevision(), LocalDateTime.now())));
        assertFalse(SimpleCandidateDiscrimination.reviewInitialCandidates(real.find("member", id)).validatedCandidateIds().contains("R23"));
        var changedPayload = mapper.readTree(answer.getAnswerPayload());
        for (var row : changedPayload.path("selections")) if ("C02".equals(row.path("comparisonId").asString()))
            ((ObjectNode) row).set("selectedOptionIds", mapper.valueToTree(List.of("B", "A")));
        var changed = new SessionAnswer(id.toString(), "Q09", changedPayload.toString(), answer.getPresentedPayload(), 10, LocalDateTime.now());
        scenarioAnswers.removeIf(a -> a.getId().equals(answer.getId()));
        scenarioAnswers.add(changed);
        when(answers.findById(answer.getId())).thenReturn(Optional.of(changed));
        assertFalse(SimpleCandidateDiscrimination.reviewInitialCandidates(real.find("member", id)).validatedCandidateIds().contains("R23"));
        var lowerRank = storedScenario("W", "Q04/P03 Q09/C02:B Q09/C02:A").find("member", id);
        assertFalse(SimpleCandidateDiscrimination.reviewInitialCandidates(lowerRank).validatedCandidateIds().contains("R23"));
        var noQ02Relation = storedScenario("W", "Q01/O Q09/C04:B").find("member", id);
        assertEquals("R03", noQ02Relation.simpleRepresentative().candidateId());
        assertTrue(noQ02Relation.decision().results().stream().anyMatch(c -> c.candidateId().equals("R21")));
        var mens = storedScenario("M", "Q01/O Q09/C01:B").find("member", id);
        assertFalse(mens.candidates().stream().anyMatch(c -> c.candidateId().equals("R21")));
    }

    SessionObservationService scenarioObservations;
    final List<SessionAnswer> scenarioAnswers = new ArrayList<>();

    // 기존 실제 마이그레이션 기반 10문항 fixture와 계산 서비스를 그대로 사용한다.
    @ParameterizedTest
    @CsvSource({"M,Q02/R03 Q10/J,R03", "W,Q02/R09 Q05/C,R09"})
    void guestTenAnswersUseRealObservationsResultsAndProductsWithoutMemberPersistence(String scope, String choices, String style) throws Exception {
        var real = storedScenario(scope, choices);
        var defId = scope.equals("M") ? definition.toString() : "6acc799d-1348-4f34-b756-593e9f5bfd52";
        var definitions = mock(TestDefinitionRepository.class);
        var def = mock(TestDefinition.class);
        when(def.getDefinitionId()).thenReturn(defId);
        when(def.getTestMode()).thenReturn("SIMPLE");
        when(def.getQuestionCount()).thenReturn((short) 10);
        when(definitions.findById(defId)).thenReturn(Optional.of(def));
        var list = new ArrayList<QuestionDefinition>();
        var input = new LinkedHashMap<String, JsonNode>();
        for (var answer : scenarioAnswers) {
            String q = answer.getId().getQuestionId();
            var item = questions.findByIdDefinitionIdAndIdQuestionId(defId, q).orElseThrow();
            when(item.getId()).thenReturn(new QuestionDefinitionId(defId, q));
            when(item.getDisplayOrder()).thenReturn(Short.parseShort(q.substring(1)));
            when(item.isRequired()).thenReturn(true);
            list.add(item);
            input.put(q, mapper.readTree(answer.getAnswerPayload()));
        }
        when(questions.findByIdDefinitionIdOrderByDisplayOrderAsc(defId)).thenReturn(list);
        var productRepo = mock(DemoProductRepository.class);
        var product = mock(DemoProduct.class);
        when(product.getProductId()).thenReturn("guest-product");
        when(product.getStyleCodes()).thenReturn(Set.of(style));
        when(product.getColorCodes()).thenReturn(Set.of("A"));
        when(product.getApparelScope()).thenReturn(DemoProduct.ApparelScope.UNISEX);
        when(productRepo.findMatchingStyles(any())).thenReturn(List.of(product));
        var sessionRepo = mock(TestSessionRepository.class);
        var guest = new GuestSimpleService(questions, definitions, scenarioObservations, real,
                new PreferenceSummaryService(sessionRepo, answers, sessions, mapper),
                new DemoProductMatchService(sessions, answers, scenarioObservations, productRepo, mapper), mapper);
        var apparel = scope.equals("M") ? ApparelScope.mens : ApparelScope.womens;
        assertEquals(10, guest.questions(apparel).questions().size());
        clearInvocations(answers, sessions, sessionRepo);
        var response = guest.submit(apparel, input);
        assertEquals(10, response.summary().evidence().stream().map(PreferenceSummaryService.Evidence::questionId).distinct().count());
        assertTrue(response.initial().finalResult());
        assertTrue(response.initial().candidates().stream().anyMatch(c -> c.candidateId().equals(style)));
        assertEquals(1, response.products().products().size());
        assertTrue(response.products().products().getFirst().evidence().stream().anyMatch(e -> style.equals(e.styleCode())));
        assertEquals(10, response.initial().revision());
        verifyNoInteractions(answers, sessions, sessionRepo);
        var invalid = new LinkedHashMap<>(input);
        invalid.put("Q05", mapper.readTree("{\"selectedOptionId\":\"NOT_AN_OPTION\"}"));
        assertEquals("INVALID_ANSWER", assertThrows(ResponseStatusException.class, () -> guest.submit(apparel, invalid)).getReason());
        invalid.put("Q05", mapper.nullNode());
        assertEquals("INVALID_ANSWER", assertThrows(ResponseStatusException.class, () -> guest.submit(apparel, invalid)).getReason());
        invalid.remove("Q10");
        assertEquals("SIMPLE_ANSWERS_INCOMPLETE", assertThrows(ResponseStatusException.class, () -> guest.submit(apparel, invalid)).getReason());
        verifyNoInteractions(answers, sessions, sessionRepo);
    }

    String neutralChoice(String scope, String choice) {
        String q = choice.substring(0, 3);
        if (choice.contains(":")) return choice.substring(0, choice.indexOf(':') + 1)
                + (choice.endsWith(":A") ? "B" : "A");
        return q + "/" + switch (q) {
            case "Q01" -> "A";
            case "Q02" -> choice.endsWith("/R12") ? "R02" : "R12";
            case "Q03" -> "S02";
            case "Q04" -> "P02";
            case "Q05", "Q06" -> "A";
            default -> scope.equals("M") ? "B" : "O";
        };
    }

    SimpleInitialCandidateService storedScenario(String scope, String choices) throws Exception {
        reset(answers);
        scenarioAnswers.clear();
        UUID def = scope.equals("M") ? definition : UUID.fromString("6acc799d-1348-4f34-b756-593e9f5bfd52");
        session(def, "SUBMITTED");
        for (int i = 13; i <= 25; i++) addCatalogCandidate("R%02d".formatted(i));
        var definitions = new TreeMap<String, JsonNode>();
        var rules = new HashMap<String, OptionScoringRule>();
        var migrations = new ArrayList<>(List.of("V13__add_mens_image_simple_definition.sql"));
        if (scope.equals("W")) migrations.addAll(List.of("V18__add_womens_simple_q05_q07.sql", "V19__add_womens_simple_q08_q10.sql"));
        for (String file : migrations) for (String line : Files.readAllLines(Path.of("src/main/resources/db/migration", file))) {
            var match = Pattern.compile("'(Q[0-9]{2})',").matcher(line);
            if (!match.find()) continue;
            String q = match.group(1);
            if (line.startsWith("INSERT INTO td_question_definition"))
                definitions.put(q, mapper.readTree(line.substring(line.indexOf("'{") + 1, line.indexOf("}','") + 1)));
            if (line.startsWith("INSERT INTO td_option_scoring_rule")) {
                var option = Pattern.compile("'" + q + "','([^']+)'").matcher(line);
                assertTrue(option.find());
                var rule = mock(OptionScoringRule.class);
                when(rule.getObservationPayload()).thenReturn(line.substring(line.lastIndexOf("'{") + 1, line.lastIndexOf("');")));
                rules.put(q + "/" + option.group(1), rule);
            }
        }
        assertEquals(10, definitions.size());
        var selected = new LinkedHashMap<String, List<String>>();
        for (String token : choices.split(" ")) if (!token.isBlank()) {
            String[] pair = token.split("/");
            String group = pair[1].contains(":") ? pair[0] + "/" + pair[1].split(":")[0] : pair[0];
            String option = pair[1].contains(":") ? pair[1].split(":")[1] : pair[1];
            selected.computeIfAbsent(group, ignored -> new ArrayList<>()).add(option);
        }
        for (var entry : definitions.entrySet()) {
            String q = entry.getKey();
            var node = entry.getValue();
            var current = mock(QuestionDefinition.class);
            when(current.getQuestionPayload()).thenReturn(node.toString());
            when(questions.findByIdDefinitionIdAndIdQuestionId(def.toString(), q)).thenReturn(Optional.of(current));
            Object payload;
            if ("COMPARISON".equals(node.path("kind").asString())) {
                var rows = new ArrayList<Map<String, Object>>();
                for (var group : node.path("comparisons")) {
                    String g = group.path("id").asString();
                    if (g.equals("C02_POCKETS")) continue;
                    String fallback = q.equals("Q09") && g.equals("C02") ? "B" : "A";
                    var options = selected.getOrDefault(q + "/" + g, List.of(fallback));
                    rows.add(Map.of("comparisonId", g,
                            "RANKED_MULTI_SELECT".equals(group.path("selectionMode").asString()) ? "selectedOptionIds" : "selectedOptionId",
                            "RANKED_MULTI_SELECT".equals(group.path("selectionMode").asString()) ? options : options.getFirst()));
                }
                payload = Map.of("selections", rows);
            } else {
                String fallback = switch (q) {
                    case "Q02" -> "R03";
                    case "Q03" -> "S02";
                    case "Q04" -> "P02";
                    case "Q07" -> "NONE";
                    case "Q10" -> scope.equals("M") ? "B" : "O";
                    default -> "A";
                };
                var options = selected.getOrDefault(q, List.of(fallback));
                payload = "MULTIPLE_CHOICE".equals(node.path("kind").asString())
                        ? Map.of("selectedOptionIds", options) : Map.of("selectedOptionId", options.getFirst());
            }
            var json = mapper.valueToTree(payload);
            switch (node.path("kind").asString()) {
                case "MULTIPLE_CHOICE" -> MultiChoiceAnswerValidator.validate(node.toString(), json, mapper);
                case "SINGLE_CHOICE" -> SingleChoiceAnswerValidator.validate(node.toString(), json, mapper);
                case "COMPARISON" -> ComparisonAnswerValidator.validate(node.toString(), json, mapper);
                default -> fail("Unknown actual question kind");
            }
            var stored = new SessionAnswer(id.toString(), q, json.toString(), node.toString(), Integer.parseInt(q.substring(1)), LocalDateTime.now());
            scenarioAnswers.add(stored);
            when(answers.findById(stored.getId())).thenReturn(Optional.of(stored));
        }
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc(id.toString())).thenAnswer(inv -> new ArrayList<>(scenarioAnswers));
        var repository = mock(TestSessionRepository.class);
        var storedSession = mock(TestSession.class);
        when(storedSession.getDefinitionId()).thenReturn(def.toString());
        when(storedSession.getQuestionOrder()).thenReturn(mapper.writeValueAsString(definitions.keySet()));
        when(repository.findBySessionIdAndMallMemberId(id.toString(), "member")).thenReturn(Optional.of(storedSession));
        var resolver = mock(SelectedOptionRuleResolver.class);
        when(resolver.resolve(eq(def.toString()), anyString(), anyList())).thenAnswer(inv -> {
            String q = inv.getArgument(1);
            List<String> options = inv.getArgument(2);
            var resolved = new HashMap<String, OptionScoringRule>();
            for (String o : options) {
                assertNotNull(rules.get(q + "/" + o), q + "/" + o);
                resolved.put(o, rules.get(q + "/" + o));
            }
            return resolved;
        });
        var raw = new SessionObservationService(answers, repository, new DirectObservationCollector(resolver), mapper);
        scenarioObservations = raw;
        var direct = new StyleCoreCandidateService(raw, mapper, mappings, null, null, null, null);
        return new SimpleInitialCandidateService(sessions, answers, questions, direct, catalog, mapper, raw);
    }

    RawObservationAggregator.RawObservation rankedRomantic(long revision, String... ordered) throws Exception {
        String line = Files.readAllLines(Path.of("src/main/resources/db/migration/V19__add_womens_simple_q08_q10.sql")).stream()
                .filter(l -> l.startsWith("INSERT INTO td_question_definition") && l.contains("'Q08',")).findFirst().orElseThrow();
        String json = line.substring(line.indexOf("'{") + 1, line.indexOf("}','") + 1);
        var selections = List.of(Map.of("comparisonId", "C01", "selectedOptionId", "A"),
                Map.of("comparisonId", "C02", "selectedOptionId", "A"), Map.of("comparisonId", "C03", "selectedOptionId", "A"),
                Map.of("comparisonId", "C04", "selectedOptionIds", List.of("A")),
                Map.of("comparisonId", "C05", "selectedOptionIds", List.of(ordered)));
        when(answers.findById(new SessionAnswerId(id.toString(), "Q08"))).thenReturn(Optional.of(new SessionAnswer(id.toString(), "Q08",
                mapper.writeValueAsString(Map.of("selections", selections)), json, revision, LocalDateTime.now())));
        int rankIndex = List.of(ordered).contains("D") ? List.of(ordered).indexOf("D") : 0;
        return new RawObservationAggregator.RawObservation("6acc799d-1348-4f34-b756-593e9f5bfd52", "RANKED_COMPARISON_PREFERENCE",
                Integer.toString(rankIndex + 1), "EXPLICIT_RANKED_SELECTION", "Q08", "C05:" + ordered[rankIndex], revision);
    }

    void assertFinal(SimpleInitialCandidateService.Response response, String... allowed) {
        assertTrue(response.finalResult());
        assertEquals("FINAL", response.decision().status());
        assertEquals(new TreeSet<>(List.of(allowed)), new TreeSet<>(response.decision().results().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList()));
        assertTrue(List.of(allowed).contains(response.decision().representativeCandidate()));
        assertEquals(SimpleCandidateDiscrimination.RULE_VERSION, response.decision().ruleVersion());
        assertTrue(response.decision().missingRequirements().isEmpty());
    }

    void addCatalogCandidate(String candidateId) {
        var entries = new ArrayList<>(catalog.findByCandidateIdIn(List.of()));
        if (entries.stream().noneMatch(c -> c.getCandidateId().equals(candidateId))) {
            var entry = mock(ResultCandidate.class);
            when(entry.getCandidateId()).thenReturn(candidateId);
            when(entry.getCandidateName()).thenReturn("Catalog " + candidateId);
            entries.add(entry);
        }
        when(catalog.findByCandidateIdIn(any())).thenReturn(entries);
    }

    RawObservationAggregator.RawObservation outfit(UUID def, String q, String optionId, long revision) throws Exception {
        String migration = def.equals(definition) ? "V13__add_mens_image_simple_definition.sql"
                : q.equals("Q10") ? "V19__add_womens_simple_q08_q10.sql" : "V18__add_womens_simple_q05_q07.sql";
        String line = Files.readAllLines(Path.of("src/main/resources/db/migration", migration)).stream()
                .filter(l -> l.startsWith("INSERT INTO td_question_definition") && l.contains("'" + q + "',")).findFirst().orElseThrow();
        String json = line.substring(line.indexOf("'{") + 1, line.indexOf("}','") + 1);
        var question = mapper.readTree(json);
        String payload = mapper.writeValueAsString(def.equals(definition) ? Map.of("selectedOptionId", optionId)
                : Map.of("selectedOptionIds", List.of(optionId)));
        when(answers.findById(new SessionAnswerId(id.toString(), q))).thenReturn(Optional.of(new SessionAnswer(
                id.toString(), q, payload, json, revision, LocalDateTime.now())));
        for (var option : question.path("options")) if (optionId.equals(option.path("id").asString()))
            return new RawObservationAggregator.RawObservation(def.toString(), q.equals("Q05") ? "DIRECT_OUTFIT_PREFERENCE"
                    : q.equals("Q06") ? "DIRECT_CLOTHING_PREFERENCE" : "DIRECT_FINAL_OUTFIT_PREFERENCE",
                    option.path("label").asString(), "EXPLICIT_SELECTION", q, optionId, revision);
        throw new AssertionError("Missing outfit option");
    }

    RawObservationAggregator.RawObservation fact(String q, String option, String type, String value, String evidence) {
        return new RawObservationAggregator.RawObservation(definition.toString(), type, value, evidence, q, option, 2);
    }
    Map<String, List<RawObservationAggregator.RawObservation>> group(List<RawObservationAggregator.RawObservation> facts) {
        return facts.stream().collect(java.util.stream.Collectors.groupingBy(RawObservationAggregator.RawObservation::type));
    }
}
