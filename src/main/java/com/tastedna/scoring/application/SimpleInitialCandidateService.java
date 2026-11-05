package com.tastedna.scoring.application;

import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.scoring.persistence.ResultCandidateRepository;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.domain.SessionAnswerId;
import com.tastedna.session.persistence.SessionAnswerRepository;
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

    public record Source(String questionId, String questionLabel, String optionId, String optionLabel,
                         String observationType, String evidenceType, long answerRevision) {}
    public record Candidate(String candidateId, String candidateName, List<Source> sources) {}
    public record Response(UUID sessionId, UUID definitionId, long revision, String status,
                           boolean finalResult, List<Candidate> candidates) {}

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
        Map<String, String> labels = new HashMap<>();
        for (var option : presented.path("options")) labels.put(option.path("id").asString(), option.path("label").asString());
        List<Candidate> result = new ArrayList<>();
        for (var item : evidence) {
            var definition = definitions.stream().filter(d -> d.isStyleCore() && d.getCandidateId().equals(item.candidateId())).findFirst();
            if (definition.isEmpty() || item.sources().isEmpty()) continue;
            var sources = item.sources().stream().sorted(Comparator.comparing(ResultCandidateEvidence.EvidenceSource::optionId))
                    .map(s -> new Source(s.questionId(), presented.path("prompt").asString(), s.optionId(), labels.get(s.optionId()),
                            s.observationType(), s.evidenceType(), s.answerRevision())).toList();
            result.add(new Candidate(item.candidateId(), definition.get().getCandidateName(), sources));
        }
        // 현재 Q02 최대 4개. 향후 초과 계약은 임의의 우선순위로 잘라내지 않고 승인을 요구한다.
        if (result.size() > 5)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "CANDIDATE_LIMIT_POLICY_REQUIRED");
        result.sort(Comparator.comparing(Candidate::candidateId));
        return new Response(sessionId, session.definitionId(), session.revision(), "INITIAL_CANDIDATES", false, List.copyOf(result));
    }
}
