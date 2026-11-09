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
        assertFalse(service.find("member", id).decision().missingRequirements().contains("CANDIDATE_LIMIT_POLICY_REQUIRED"));
        select(definition, "R01", "R02", "R03", "R04", "R05", "R06");
        assertEquals(6, service.find("member", id).candidates().size());
        assertTrue(service.find("member", id).decision().missingRequirements().contains("CANDIDATE_LIMIT_POLICY_REQUIRED"));
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
                assertEquals("DEFERRED", response.decision().status());
                assertFalse(response.finalResult());
                assertTrue(response.decision().results().isEmpty());
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
        assertEquals("DEFERRED", updated.decision().status());
        assertFalse(updated.finalResult());
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
        assertEquals("DEFERRED", response.decision().status());
        assertNull(response.simpleRepresentative());
        select(definition, "R02", "R03", "R04", "R05", "R06");
        raw.add(fact("Q02", "R06", "DIRECT_FASHION_LOOK_PREFERENCE", label("R06"), "EXPLICIT_SELECTION"));
        when(observations.collectRaw("member", id.toString())).thenReturn(group(raw));
        var expanded = service.find("member", id);
        assertEquals(List.of("R02", "R03", "R04", "R05", "R06", "R07"), expanded.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertTrue(expanded.decision().missingRequirements().contains("CANDIDATE_LIMIT_POLICY_REQUIRED"));
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
        assertEquals("DEFERRED", changed.decision().status());
        assertFalse(changed.finalResult());

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
            assertEquals("DEFERRED", response.decision().status());
            assertFalse(response.finalResult());
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

    @Test void realFourStyleAnswerPlusComparisonAndOutfitRequiresReductionPolicy() throws Exception {
        addCatalogCandidate("R16");
        select(definition, "R02", "R03", "R04", "R05");
        var facts = new ArrayList<>(observations.collectRaw("member", id.toString()).get("DIRECT_FASHION_LOOK_PREFERENCE"));
        facts.add(blackComparison("B", 3));
        facts.add(outfit(definition, "Q05", "B", 4));
        facts.add(facts.getLast());
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        var response = service.find("member", id);
        assertEquals(List.of("R02", "R03", "R04", "R05", "R07", "R16"), response.candidates().stream().map(SimpleInitialCandidateService.Candidate::candidateId).toList());
        assertTrue(response.decision().missingRequirements().contains("CANDIDATE_LIMIT_POLICY_REQUIRED"));
        assertEquals("DEFERRED", response.decision().status());
        assertFalse(response.finalResult());
        assertTrue(response.decision().results().isEmpty());
        assertNull(response.simpleRepresentative());
        var support = response.candidates().getLast().evidence().positive();
        assertEquals(1, support.size());
        assertEquals("Q05", support.getFirst().questionId());
        assertEquals("B", support.getFirst().optionId());
        assertEquals(4, support.getFirst().answerRevision());
        assertEquals(3, response.candidates().get(4).evidence().positive().getFirst().answerRevision());
        Collections.reverse(facts);
        when(observations.collectRaw("member", id.toString())).thenReturn(group(facts));
        assertEquals(response, service.find("member", id));
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
