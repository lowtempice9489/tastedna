package com.tastedna.scoring.application;

import com.tastedna.session.persistence.*;
import com.tastedna.session.api.dto.SessionResponse;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.application.comparison.*;
import com.tastedna.session.application.multi.MultiChoiceAnswerPayloadExtractor;
import com.tastedna.session.application.single.SingleChoiceAnswerPayloadExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;

// 답변 당시 정의에서 근거를 복원한다. 미선택 비선호는 명시된 순위형 그룹 안에서만 생성한다.
@Service
public class PreferenceSummaryService {
    private final TestSessionRepository sessions;
    private final SessionAnswerRepository answers;
    private final TestSessionService sessionService;
    private final JsonMapper mapper;
    public PreferenceSummaryService(TestSessionRepository sessions, SessionAnswerRepository answers,
                                    TestSessionService sessionService, JsonMapper mapper) {
        this.sessions = sessions; this.answers = answers; this.sessionService = sessionService; this.mapper = mapper;
    }
    public record Selection(String optionId, String label, List<String> images,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Integer rank) {
        public Selection(String optionId, String label, List<String> images) { this(optionId, label, images, null); }
    }
    public record Evidence(String questionId, String comparisonId, String prompt, String meaning,
                           long answerRevision, List<Selection> selections, List<Selection> alternatives,
                           @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Selection> notPreferred) {
        public Evidence(String questionId, String comparisonId, String prompt, String meaning,
                        long answerRevision, List<Selection> selections, List<Selection> alternatives) {
            this(questionId, comparisonId, prompt, meaning, answerRevision, selections, alternatives, List.of());
        }
    }
    public record AnswerSnapshot(SessionResponse session, Map<String, JsonNode> answers) {}
    public record Summary(SessionResponse session, String status, String title, String description,
                          boolean comprehensiveResult, List<Evidence> evidence) {}

    // 답변 저장과 같은 행 잠금을 사용해 revision과 답변을 한 시점에 읽는다.
    @Transactional
    public AnswerSnapshot snapshot(String member, UUID id) {
        lock(member, id);
        Map<String, JsonNode> payloads = new LinkedHashMap<>();
        for (var answer : answers.findByIdSessionIdOrderByIdQuestionIdAsc(id.toString()))
            payloads.put(answer.getId().getQuestionId(), mapper.readTree(answer.getAnswerPayload()));
        return new AnswerSnapshot(sessionService.get(member, id), Collections.unmodifiableMap(payloads));
    }

    @Transactional
    public Summary summarize(String member, UUID id) {
        var session = lock(member, id);
        if (!"SUBMITTED".equals(session.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NOT_SUBMITTED");
        var response = sessionService.get(member, id);
        if (!"SIMPLE".equals(response.mode().name()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SIMPLE_SESSION_REQUIRED");
        var stored = answers.findByIdSessionIdOrderByIdQuestionIdAsc(id.toString());
        Set<String> expected = new HashSet<>();
        for (int i = 1; i <= 10; i++) expected.add("Q%02d".formatted(i));
        if (!stored.stream().map(a -> a.getId().getQuestionId()).collect(java.util.stream.Collectors.toSet()).equals(expected))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SIMPLE_ANSWERS_INCOMPLETE");
        List<Evidence> evidence = new ArrayList<>();
        for (var answer : stored) {
            String qid = answer.getId().getQuestionId();
            JsonNode question = mapper.readTree(answer.getPresentedPayload());
            JsonNode payload = mapper.readTree(answer.getAnswerPayload());
            String kind = question.path("kind").asString();
            if ("COMPARISON".equals(kind)) {
                ComparisonAnswerValidator.validate(answer.getPresentedPayload(), payload, mapper);
                var selected = ComparisonAnswerPayloadExtractor.extractSelections(payload);
                for (JsonNode group : ComparisonAnswerValidator.activeComparisons(question, selected)) {
                    String cid = group.path("id").asString();
                    var selection = selected.get(cid);
                    if (selection.ranked()) {
                        List<Selection> ranked = new ArrayList<>();
                        for (String optionId : selection.optionIds()) {
                            var item = select(group, Set.of(optionId), true).getFirst();
                            ranked.add(new Selection(item.optionId(), item.label(), item.images(), ranked.size() + 1));
                        }
                        evidence.add(new Evidence(qid, cid, group.path("label").asString(), "RANKED_COMPARATIVE_PREFERENCE",
                                answer.getAnswerRevision(), List.copyOf(ranked), List.of(),
                                select(group, new HashSet<>(selection.notPreferredIds(group)), true)));
                    } else {
                        var ids = new HashSet<>(selection.optionIds());
                        evidence.add(new Evidence(qid, cid, group.path("label").asString(), "COMPARATIVE_PREFERENCE",
                                answer.getAnswerRevision(), select(group, ids, true), select(group, ids, false)));
                    }
                }
            } else {
                Set<String> selected = "MULTIPLE_CHOICE".equals(kind)
                        ? new HashSet<>(MultiChoiceAnswerPayloadExtractor.extract(payload))
                        : Set.of(SingleChoiceAnswerPayloadExtractor.extract(payload));
                evidence.add(new Evidence(qid, null, question.path("prompt").asString(),
                        "Q07".equals(qid) ? (selected.contains("NONE") ? "NO_EXPLICIT_AVOIDANCE" : "EXPLICIT_AVOIDANCE") : "DIRECT_PREFERENCE",
                        answer.getAnswerRevision(), select(question, selected, true), List.of()));
            }
        }
        String styles = evidence.stream().filter(e -> e.questionId().equals("Q02"))
                .flatMap(e -> e.selections().stream()).map(Selection::label)
                .collect(java.util.stream.Collectors.joining(" · "));
        return new Summary(response, "DIRECT_PREFERENCE_SUMMARY",
                styles.isBlank() ? "나의 취향 기록" : styles,
                "직접 고른 스타일과 착장을 모았어요. 하나의 유형으로 단정하지 않고, 좋아하는 것과 피하고 싶은 것을 함께 보여드려요.",
                false, List.copyOf(evidence));
    }
    private com.tastedna.session.domain.TestSession lock(String member, UUID id) {
        return sessions.findByIdAndMemberIdForUpdate(id.toString(), member)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"));
    }
    private List<Selection> select(JsonNode group, Set<String> chosen, boolean include) {
        List<Selection> result = new ArrayList<>();
        for (JsonNode option : group.path("options")) {
            String id = option.path("id").asString();
            if (chosen.contains(id) != include) continue;
            List<String> images = new ArrayList<>();
            for (JsonNode image : option.path("images")) images.add(image.asString());
            result.add(new Selection(id, option.path("label").asString(), List.copyOf(images)));
        }
        if (include && result.size() != chosen.size()) throw new IllegalStateException("INVALID_STORED_SELECTION");
        return List.copyOf(result);
    }
}

