package com.tastedna.scoring.application;

import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.scoring.persistence.ResultCandidateRepository;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.domain.SessionAnswerId;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.application.multi.MultiChoiceAnswerPayloadExtractor;
import com.tastedna.session.application.single.SingleChoiceAnswerPayloadExtractor;
import com.tastedna.session.application.comparison.ComparisonAnswerPayloadExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SimpleInitialCandidateService {
    private final TestSessionService sessions;
    private final SessionAnswerRepository answers;
    private final QuestionDefinitionRepository questions;
    private final StyleCoreCandidateService candidates;
    private final ResultCandidateRepository catalog;
    private final JsonMapper mapper;
    private final SessionObservationService observations;

    public record Source(String questionId, String questionLabel, String optionId, String optionLabel,
                         String observationType, String evidenceType, long answerRevision, Integer rank) {
        public Source(String questionId, String questionLabel, String optionId, String optionLabel,
                      String observationType, String evidenceType, long answerRevision) {
            this(questionId, questionLabel, optionId, optionLabel, observationType, evidenceType, answerRevision, null);
        }
    }
    public record Observation(String definitionId, String questionId, String optionId, String comparisonId,
                              String observationType, String observedValue, String evidenceType,
                              long answerRevision, Integer rank) {}
    public record CandidateEvidence(List<Source> positive, List<Observation> avoidanceOrConflict,
                                    List<Observation> unresolved, String status) {}
    public record Candidate(String candidateId, String candidateName, List<Source> sources,
                            CandidateEvidence evidence) {}
    public record Decision(String status, List<Candidate> results, List<String> missingRequirements,
                           String representativeCandidate, String ruleVersion) {
        public Decision(String status, List<Candidate> results, List<String> missingRequirements) {
            this(status, results, missingRequirements, null, SimpleCandidateDiscrimination.RULE_VERSION);
        }
    }
    public record Response(UUID sessionId, UUID definitionId, long revision, String status,
                           boolean finalResult, List<Candidate> candidates, Decision decision,
                           Candidate simpleRepresentative) {}

    @Transactional(readOnly = true)
    public Response find(String memberId, UUID sessionId) {
        var session = sessions.get(memberId, sessionId);
        if (!"SUBMITTED".equals(session.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NOT_SUBMITTED");
        if (!"SIMPLE".equals(session.mode().name()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SIMPLE_SESSION_REQUIRED");
        var answer = answers.findById(new SessionAnswerId(sessionId.toString(), "Q02"));
        var presented = answer.map(a -> mapper.readTree(a.getPresentedPayload())).orElseGet(mapper::createObjectNode);
        // 남녀 공통으로 승인된 이미지 Q02 계약을 기준으로 삼고, 세션의 실제 정의 ID는 보존한다.
        var approved = questions.findByIdDefinitionIdAndIdQuestionId(
                "33333333-3333-4333-8333-333333333333", "Q02")
                .orElseThrow(() -> new IllegalStateException("APPROVED_Q02_NOT_FOUND"));
        var evidence = answer.isPresent() ? candidates.extractCompatibleDirectEvidence(memberId, sessionId.toString(),
                session.definitionId().toString(), mapper.readTree(approved.getQuestionPayload()), presented)
                : List.<ResultCandidateEvidence>of();
        var facts = observationFacts(observations.collectRaw(memberId, sessionId.toString()), session.definitionId().toString());
        var combinationSupport = mensBlackCombinationSupport(sessionId, session.definitionId().toString(), facts);
        Map<String, String> labels = new HashMap<>();
        for (var option : presented.path("options")) labels.put(option.path("id").asString(), option.path("label").asString());
        Map<String, List<Source>> directSources = new HashMap<>();
        for (var item : evidence) {
            var sources = item.sources().stream().distinct().sorted(Comparator.comparing(ResultCandidateEvidence.EvidenceSource::optionId))
                    .map(s -> new Source(s.questionId(), presented.path("prompt").asString(), s.optionId(), labels.get(s.optionId()),
                            s.observationType(), s.evidenceType(), s.answerRevision())).toList();
            if (!sources.isEmpty()) directSources.put(item.candidateId(), sources);
        }
        Map<String, List<Source>> additionalSources = new HashMap<>();
        for (var source : combinationSupport)
            additionalSources.computeIfAbsent("C03:A".equals(source.optionId()) ? "R01" : "R07", ignored -> new ArrayList<>()).add(source);
        addOutfitEntrySupport(sessionId, session.definitionId().toString(), facts, additionalSources);
        var rankedSupport = womensExpressiveRomanticSupport(sessionId, session.definitionId().toString(), facts);
        if (!rankedSupport.isEmpty()) additionalSources.put("R15", rankedSupport);
        // 승인된 진입 관계의 합집합만 조회한다. 카탈로그 등록이나 후보 그룹 자체는 진입 근거가 아니다.
        Set<String> candidateIds = new HashSet<>(directSources.keySet());
        candidateIds.addAll(additionalSources.keySet());
        var definitions = catalog.findByCandidateIdIn(candidateIds);
        List<Candidate> result = new ArrayList<>();
        for (String candidateId : candidateIds) {
            var definition = definitions.stream().filter(d -> d.getCandidateId().equals(candidateId)).findFirst();
            if (definition.isEmpty()) continue;
            var sources = directSources.getOrDefault(candidateId, List.of());
            var positive = new ArrayList<>(sources);
            positive.addAll(additionalSources.getOrDefault(candidateId, List.of()));
            // 회피 사실도 후보와의 승인된 관계가 없으면 충돌로 승격하지 않는다.
            var unresolved = facts.stream().filter(f -> positive.stream().noneMatch(s ->
                    s.questionId().equals(f.questionId()) && s.optionId().equals(f.optionId())
                            && s.observationType().equals(f.observationType()) && s.evidenceType().equals(f.evidenceType())
                            && s.answerRevision() == f.answerRevision()
                            && (s.rank() == null ? s.optionLabel().equals(f.observedValue()) : s.rank().equals(f.rank())))).toList();
            result.add(new Candidate(candidateId, definition.get().getCandidateName(), sources,
                    new CandidateEvidence(List.copyOf(positive), List.of(), unresolved, "CANDIDATE_RELATIONS_PENDING")));
        }
        result.sort(Comparator.comparing(Candidate::candidateId));
        var selected = answer.map(a -> MultiChoiceAnswerPayloadExtractor.extract(mapper.readTree(a.getAnswerPayload())))
                .orElse(List.of());
        // 최신 단일 직접 선택의 Simple 대표는 종합 RESULT 대표와 별개로 보존한다.
        var directCandidates = result.stream().filter(c -> !c.sources().isEmpty()).toList();
        Candidate representative = selected.size() == 1 && directCandidates.size() == 1
                && directCandidates.getFirst().sources().stream().allMatch(s -> selected.contains(s.optionId())
                && s.answerRevision() == answer.orElseThrow().getAnswerRevision()) ? directCandidates.getFirst() : null;
        var initial = new Response(sessionId, session.definitionId(), session.revision(), "INITIAL_CANDIDATES", false,
                List.copyOf(result), new Decision("DEFERRED", List.of(), List.of()), representative);
        var review = SimpleCandidateDiscrimination.reviewInitialCandidates(initial);
        var valid = SimpleCandidateDiscrimination.reduceEquivalentValidatedCandidates(sessionId, session.revision(),
                review.validatedCandidateIds(), SimpleCandidateDiscrimination.RULE_VERSION);
        var selectedResult = SimpleCandidateDiscrimination.selectValidatedRepresentative(sessionId, session.revision(),
                valid, SimpleCandidateDiscrimination.RULE_VERSION);
        var finalResults = result.stream().filter(c -> valid.contains(c.candidateId())).toList();
        var missing = new ArrayList<>(review.missingRequirements());
        if (result.isEmpty()) missing.addFirst("MAPPED_DIRECT_PREFERENCE_EVIDENCE");
        return new Response(sessionId, session.definitionId(), session.revision(), "INITIAL_CANDIDATES", !finalResults.isEmpty(),
                initial.candidates(), new Decision(finalResults.isEmpty() ? "DEFERRED" : "FINAL", finalResults, List.copyOf(missing),
                        selectedResult.orElse(null), SimpleCandidateDiscrimination.RULE_VERSION), representative);
    }

    // 정확한 전체 착장의 긍정 관측만 연결한다. 최종 충분성은 별도 B 정책에서 판정한다.
    private void addOutfitEntrySupport(UUID sessionId, String definitionId, List<Observation> facts,
                                      Map<String, List<Source>> support) {
        if ("33333333-3333-4333-8333-333333333333".equals(definitionId)) {
            // 09B: 블레이저·셔츠·슬랙스와 스웨트셔츠 일상 착장을 Core 근거로 한정한다.
            addOutfitSource(sessionId, facts, support, "Q10", "J", "R03", "DIRECT_FINAL_OUTFIT_PREFERENCE", "/assets/gender/male/Q10/J.png");
            addOutfitSource(sessionId, facts, support, "Q10", "A", "R05", "DIRECT_FINAL_OUTFIT_PREFERENCE", "/assets/gender/male/Q10/Q10_A.png");
            addOutfitSource(sessionId, facts, support, "Q10", "D", "R13", "DIRECT_FINAL_OUTFIT_PREFERENCE", "/assets/gender/male/Q10/Q10_D.png");
            addOutfitSource(sessionId, facts, support, "Q05", "B", "R16", "DIRECT_OUTFIT_PREFERENCE", "/assets/gender/male/Q05/TasteDNA_Q05_B.png");
            addOutfitSource(sessionId, facts, support, "Q05", "C", "R17", "DIRECT_OUTFIT_PREFERENCE", "/assets/gender/male/Q05/TasteDNA_Q05_C.png");
        } else if ("6acc799d-1348-4f34-b756-593e9f5bfd52".equals(definitionId)) {
            // 09B: 스쿨룩·일상 착장·셔링 블라우스/플레어 스커트의 직접 선택만 사용한다.
            addOutfitSource(sessionId, facts, support, "Q10", "I", "R04", "DIRECT_FINAL_OUTFIT_PREFERENCE", "/assets/gender/female/Q10/I.png");
            addOutfitSource(sessionId, facts, support, "Q10", "A", "R05", "DIRECT_FINAL_OUTFIT_PREFERENCE", "/assets/gender/female/Q10/A.png");
            addOutfitSource(sessionId, facts, support, "Q05", "C", "R09", "DIRECT_OUTFIT_PREFERENCE", "/assets/gender/female/Q05/C.png");
            addOutfitSource(sessionId, facts, support, "Q05", "B", "R13", "DIRECT_OUTFIT_PREFERENCE", "/assets/gender/female/Q05/B.png");
            addOutfitSource(sessionId, facts, support, "Q10", "G", "R13", "DIRECT_FINAL_OUTFIT_PREFERENCE", "/assets/gender/female/Q10/G.png");
            addOutfitSource(sessionId, facts, support, "Q06", "B", "R17", "DIRECT_CLOTHING_PREFERENCE", "/assets/gender/female/Q06/B.png");
            addOutfitSource(sessionId, facts, support, "Q10", "H", "R17", "DIRECT_FINAL_OUTFIT_PREFERENCE", "/assets/gender/female/Q10/H.png");
        }
    }

    private void addOutfitSource(UUID sessionId, List<Observation> facts, Map<String, List<Source>> support,
                                String questionId, String optionId, String candidateId, String type, String image) {
        var answer = answers.findById(new SessionAnswerId(sessionId.toString(), questionId));
        if (answer.isEmpty()) return;
        var stored = answer.get();
        var presented = mapper.readTree(stored.getPresentedPayload());
        var payload = mapper.readTree(stored.getAnswerPayload());
        boolean selected = switch (presented.path("kind").asString()) {
            case "SINGLE_CHOICE" -> optionId.equals(SingleChoiceAnswerPayloadExtractor.extract(payload));
            case "MULTIPLE_CHOICE" -> MultiChoiceAnswerPayloadExtractor.extract(payload).contains(optionId);
            default -> false;
        };
        if (!selected) return;
        for (var option : presented.path("options")) {
            if (!optionId.equals(option.path("id").asString()) || option.path("images").size() != 1
                    || !image.equals(option.path("images").path(0).asString())) continue;
            String label = option.path("label").asString();
            var sources = facts.stream().filter(f -> questionId.equals(f.questionId()) && optionId.equals(f.optionId())
                            && type.equals(f.observationType()) && "EXPLICIT_SELECTION".equals(f.evidenceType())
                            && stored.getAnswerRevision() == f.answerRevision() && label.equals(f.observedValue()))
                    .map(f -> new Source(questionId, presented.path("prompt").asString(), optionId, label,
                            type, f.evidenceType(), f.answerRevision())).distinct().toList();
            if (!sources.isEmpty()) support.computeIfAbsent(candidateId, ignored -> new ArrayList<>()).addAll(sources);
        }
    }

    // C05의 D 최상위 선택만 인정한다. 후순위·미선택을 후보 회피로 바꾸지 않는다.
    private List<Source> womensExpressiveRomanticSupport(UUID sessionId, String definitionId, List<Observation> facts) {
        if (!"6acc799d-1348-4f34-b756-593e9f5bfd52".equals(definitionId)) return List.of();
        var answer = answers.findById(new SessionAnswerId(sessionId.toString(), "Q08"));
        if (answer.isEmpty()) return List.of();
        var stored = answer.get();
        var selection = ComparisonAnswerPayloadExtractor.extractSelections(mapper.readTree(stored.getAnswerPayload())).get("C05");
        if (selection == null || !selection.ranked() || selection.optionIds().isEmpty()
                || !"D".equals(selection.optionIds().getFirst())) return List.of();
        var presented = mapper.readTree(stored.getPresentedPayload());
        for (var group : presented.path("comparisons")) {
            if (!"C05".equals(group.path("id").asString()) || !"RANKED_MULTI_SELECT".equals(group.path("selectionMode").asString())) continue;
            for (var option : group.path("options")) {
                if (!"D".equals(option.path("id").asString()) || option.path("images").size() != 1
                        || !"/assets/gender/female/Q08/C05/Q08_C05_D.png".equals(option.path("images").path(0).asString())) continue;
                return facts.stream().filter(f -> "Q08".equals(f.questionId()) && "C05:D".equals(f.optionId())
                                && "RANKED_COMPARISON_PREFERENCE".equals(f.observationType())
                                && "EXPLICIT_RANKED_SELECTION".equals(f.evidenceType()) && Integer.valueOf(1).equals(f.rank())
                                && stored.getAnswerRevision() == f.answerRevision())
                        .map(f -> new Source("Q08", presented.path("prompt").asString(), "C05:D", option.path("label").asString(),
                                f.observationType(), f.evidenceType(), f.answerRevision(), f.rank())).distinct().toList();
            }
        }
        return List.of();
    }

    private List<Source> mensBlackCombinationSupport(UUID sessionId, String definitionId, List<Observation> facts) {
        if (!"33333333-3333-4333-8333-333333333333".equals(definitionId)) return List.of();
        var answer = answers.findById(new SessionAnswerId(sessionId.toString(), "Q09"));
        if (answer.isEmpty()) return List.of();
        var stored = answer.get();
        var selected = ComparisonAnswerPayloadExtractor.extractSelections(mapper.readTree(stored.getAnswerPayload())).get("C03");
        if (selected == null || selected.ranked() || selected.optionIds().size() != 1) return List.of();
        String optionId = selected.optionIds().getFirst();
        if (!Set.of("A", "B").contains(optionId)) return List.of();
        var presented = mapper.readTree(stored.getPresentedPayload());
        for (var comparison : presented.path("comparisons")) {
            if (!"C03".equals(comparison.path("id").asString())) continue;
            for (var option : comparison.path("options")) {
                if (!optionId.equals(option.path("id").asString())) continue;
                String label = option.path("label").asString();
                return facts.stream().filter(f -> definitionId.equals(f.definitionId())
                                && "Q09".equals(f.questionId()) && ("C03:" + optionId).equals(f.optionId())
                                && "DIRECT_COMBINATION_PREFERENCE".equals(f.observationType())
                                && "EXPLICIT_SELECTION".equals(f.evidenceType())
                                && stored.getAnswerRevision() == f.answerRevision() && label.equals(f.observedValue()))
                        .map(f -> new Source("Q09", presented.path("prompt").asString(), f.optionId(), label,
                                f.observationType(), f.evidenceType(), f.answerRevision())).distinct().toList();
            }
        }
        return List.of();
    }

    // 관측 사실을 한 번씩 보존한다. 순위는 해당 비교 그룹 안에서만 유효하다.
    static List<Observation> observationFacts(Map<String, List<RawObservationAggregator.RawObservation>> raw, String definitionId) {
        return raw.values().stream().flatMap(Collection::stream)
                .filter(o -> definitionId.equals(o.definitionId()))
                .map(o -> {
                    boolean comparison = o.type().equals("DIRECT_COMPARISON_PREFERENCE")
                            || o.type().equals("DIRECT_COMBINATION_PREFERENCE")
                            || o.type().startsWith("RANKED_COMPARISON_");
                    int separator = o.optionId().indexOf(':');
                    String group = comparison && separator > 0 ? o.optionId().substring(0, separator) : null;
                    Integer rank = "RANKED_COMPARISON_PREFERENCE".equals(o.type()) ? Integer.valueOf(o.observedValue()) : null;
                    return new Observation(o.definitionId(), o.questionId(), o.optionId(), group, o.type(),
                            o.observedValue(), o.evidence(), o.answerRevision(), rank);
                }).distinct().sorted(Comparator.comparing(Observation::questionId)
                        .thenComparing(o -> Objects.toString(o.comparisonId(), ""))
                        .thenComparing(Observation::observationType)
                        .thenComparing(Observation::rank, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Observation::optionId).thenComparing(Observation::evidenceType)
                        .thenComparing(Observation::answerRevision).thenComparing(Observation::observedValue))
                .toList();
    }
}
