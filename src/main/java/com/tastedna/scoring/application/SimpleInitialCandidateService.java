package com.tastedna.scoring.application;

import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.scoring.persistence.ResultCandidateRepository;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.domain.SessionAnswerId;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.application.multi.MultiChoiceAnswerPayloadExtractor;
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
                         String observationType, String evidenceType, long answerRevision) {}
    public record Observation(String definitionId, String questionId, String optionId, String comparisonId,
                              String observationType, String observedValue, String evidenceType,
                              long answerRevision, Integer rank) {}
    public record CandidateEvidence(List<Source> positive, List<Observation> avoidanceOrConflict,
                                    List<Observation> unresolved, String status) {}
    public record Candidate(String candidateId, String candidateName, List<Source> sources,
                            CandidateEvidence evidence) {}
    public record Decision(String status, List<Candidate> results, List<String> missingRequirements) {}
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
        var answer = answers.findById(new SessionAnswerId(sessionId.toString(), "Q02"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Q02_ANSWER_REQUIRED"));
        var presented = mapper.readTree(answer.getPresentedPayload());
        // 남녀 공통으로 승인된 이미지 Q02 계약을 기준으로 삼고, 세션의 실제 정의 ID는 보존한다.
        var approved = questions.findByIdDefinitionIdAndIdQuestionId(
                "33333333-3333-4333-8333-333333333333", "Q02")
                .orElseThrow(() -> new IllegalStateException("APPROVED_Q02_NOT_FOUND"));
        var evidence = candidates.extractCompatibleDirectEvidence(memberId, sessionId.toString(),
                session.definitionId().toString(), mapper.readTree(approved.getQuestionPayload()), presented);
        var definitions = catalog.findByCandidateIdIn(evidence.stream().map(ResultCandidateEvidence::candidateId).toList());
        var facts = observationFacts(observations.collectRaw(memberId, sessionId.toString()), session.definitionId().toString());
        var combinationSupport = mensBlackCombinationSupport(sessionId, session.definitionId().toString(), facts);
        Map<String, String> labels = new HashMap<>();
        for (var option : presented.path("options")) labels.put(option.path("id").asString(), option.path("label").asString());
        List<Candidate> result = new ArrayList<>();
        for (var item : evidence) {
            var definition = definitions.stream().filter(d -> d.isStyleCore() && d.getCandidateId().equals(item.candidateId())).findFirst();
            if (definition.isEmpty() || item.sources().isEmpty()) continue;
            var sources = item.sources().stream().distinct().sorted(Comparator.comparing(ResultCandidateEvidence.EvidenceSource::optionId))
                    .map(s -> new Source(s.questionId(), presented.path("prompt").asString(), s.optionId(), labels.get(s.optionId()),
                            s.observationType(), s.evidenceType(), s.answerRevision())).toList();
            var positive = new ArrayList<>(sources);
            // 승인된 남성복 비교는 기존 후보만 보조하며 Q02 sources와 대표 스타일 조건은 보존한다.
            combinationSupport.stream().filter(s ->
                    ("R01".equals(item.candidateId()) && "C03:A".equals(s.optionId()))
                    || ("R07".equals(item.candidateId()) && "C03:B".equals(s.optionId())))
                    .forEach(positive::add);
            // 회피 사실도 후보와의 승인된 관계가 없으면 충돌로 승격하지 않는다.
            var unresolved = facts.stream().filter(f -> positive.stream().noneMatch(s ->
                    s.questionId().equals(f.questionId()) && s.optionId().equals(f.optionId())
                            && s.observationType().equals(f.observationType()) && s.evidenceType().equals(f.evidenceType())
                            && s.answerRevision() == f.answerRevision()
                            && s.optionLabel().equals(f.observedValue()))).toList();
            result.add(new Candidate(item.candidateId(), definition.get().getCandidateName(), sources,
                    new CandidateEvidence(List.copyOf(positive), List.of(), unresolved, "CANDIDATE_RELATIONS_PENDING")));
        }
        // 현재 Q02 최대 4개. 향후 초과 계약은 임의의 우선순위로 잘라내지 않고 승인을 요구한다.
        if (result.size() > 5)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "CANDIDATE_LIMIT_POLICY_REQUIRED");
        result.sort(Comparator.comparing(Candidate::candidateId));
        // Q02 직접 선택은 초기 후보 근거다. 승인된 구별·확정 규칙 없이 최종 RESULT로 승격하지 않는다.
        List<String> missing = new ArrayList<>();
        if (result.isEmpty()) missing.add("MAPPED_DIRECT_PREFERENCE_EVIDENCE");
        missing.addAll(List.of("VERIFIED_CANDIDATE_EVIDENCE_MAPPING",
                "NEGATIVE_AND_RANKED_EVIDENCE_INTERPRETATION",
                "CANDIDATE_DISCRIMINATION_RULE", "FINALIZATION_RULE"));
        var selected = MultiChoiceAnswerPayloadExtractor.extract(mapper.readTree(answer.getAnswerPayload()));
        // 최신 단일 직접 선택만 Simple 대표로 표시하며 종합 RESULT의 보류 상태는 유지한다.
        Candidate representative = selected.size() == 1 && result.size() == 1
                && result.getFirst().sources().stream().allMatch(s -> selected.contains(s.optionId())
                && s.answerRevision() == answer.getAnswerRevision()) ? result.getFirst() : null;
        return new Response(sessionId, session.definitionId(), session.revision(), "INITIAL_CANDIDATES", false,
                List.copyOf(result), new Decision("DEFERRED", List.of(), List.copyOf(missing)), representative);
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
