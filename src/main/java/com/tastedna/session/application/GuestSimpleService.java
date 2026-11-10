package com.tastedna.session.application;

import com.tastedna.definition.api.dto.QuestionResponse;
import com.tastedna.definition.persistence.QuestionDefinitionRepository;
import com.tastedna.definition.persistence.TestDefinitionRepository;
import com.tastedna.demo.DemoProductMatchService;
import com.tastedna.scoring.application.PreferenceSummaryService;
import com.tastedna.scoring.application.SessionObservationService;
import com.tastedna.scoring.application.SimpleInitialCandidateService;
import com.tastedna.session.api.dto.ApparelScope;
import com.tastedna.session.api.dto.Mode;
import com.tastedna.session.api.dto.SessionResponse;
import com.tastedna.session.application.comparison.ComparisonAnswerValidator;
import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import com.tastedna.session.application.single.SingleChoiceAnswerValidator;
import com.tastedna.session.domain.SessionAnswer;
import com.tastedna.session.domain.TestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// 비회원 응답은 요청 처리 메모리에서만 검증·계산한다. 세션/답변/보관 Repository에 쓰지 않는다.
@Service
@RequiredArgsConstructor
public class GuestSimpleService {
    private final QuestionDefinitionRepository questions;
    private final TestDefinitionRepository definitions;
    private final SessionObservationService observations;
    private final SimpleInitialCandidateService results;
    private final PreferenceSummaryService summaries;
    private final DemoProductMatchService products;
    private final JsonMapper mapper;

    public record Questions(UUID definitionId, List<QuestionResponse> questions) {}
    public record Result(PreferenceSummaryService.Summary summary, SimpleInitialCandidateService.Response initial,
                         DemoProductMatchService.Response products) {}

    @Transactional(readOnly = true)
    public Questions questions(ApparelScope scope) {
        String id = scope == ApparelScope.mens ? "33333333-3333-4333-8333-333333333333" : "6acc799d-1348-4f34-b756-593e9f5bfd52";
        var definition = definitions.findById(id)
                .filter(d -> "SIMPLE".equals(d.getTestMode()) && d.getQuestionCount() == 10)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "IMAGE_DEFINITION_NOT_READY"));
        var list = questions.findByIdDefinitionIdOrderByDisplayOrderAsc(definition.getDefinitionId());
        if (list.size() != 10) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "IMAGE_DEFINITION_NOT_READY");
        for (int i = 0; i < 10; i++)
            if (!"Q%02d".formatted(i + 1).equals(list.get(i).getId().getQuestionId()))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "IMAGE_DEFINITION_NOT_READY");
        return new Questions(UUID.fromString(id), list.stream().map(q -> new QuestionResponse(q.getId().getQuestionId(),
                q.getDisplayOrder(), q.isRequired(), mapper.readTree(q.getQuestionPayload()))).toList());
    }

    @Transactional(readOnly = true)
    public Result submit(ApparelScope scope, Map<String, JsonNode> input) {
        var definition = questions(scope);
        var expected = definition.questions().stream().map(QuestionResponse::questionId).collect(Collectors.toSet());
        if (input == null || !expected.equals(input.keySet()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SIMPLE_ANSWERS_INCOMPLETE");
        if (mapper.writeValueAsString(input).length() > 65536)
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "INVALID_REQUEST_BODY");
        UUID id = UUID.randomUUID();
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var transientSession = new TestSession(id.toString(), null, definition.definitionId().toString(), scope.name().toUpperCase(),
                mapper.writeValueAsString(definition.questions().stream().map(QuestionResponse::questionId).toList()), now, now);
        Map<String, SessionAnswer> answers = new LinkedHashMap<>();
        for (var question : definition.questions()) {
            var payload = input.get(question.questionId());
            String presented = mapper.writeValueAsString(question.questionPayload());
            try {
                switch (question.questionPayload().path("kind").asString()) {
                    case "MULTIPLE_CHOICE" -> MultiChoiceAnswerValidator.validate(presented, payload, mapper);
                    case "SINGLE_CHOICE" -> SingleChoiceAnswerValidator.validate(presented, payload, mapper);
                    case "COMPARISON" -> ComparisonAnswerValidator.validate(presented, payload, mapper);
                    default -> throw new IllegalArgumentException("INVALID_QUESTION_DEFINITION");
                }
            } catch (IllegalArgumentException invalid) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ANSWER", invalid);
            }
            transientSession.advanceRevision(transientSession.getRevision());
            answers.put(question.questionId(), new SessionAnswer(id.toString(), question.questionId(),
                    mapper.writeValueAsString(payload), presented, transientSession.getRevision(), now));
        }
        transientSession.submit(now);
        var session = new SessionResponse(id, Mode.SIMPLE, scope, "SUBMITTED", transientSession.getRevision(), definition.definitionId(), null);
        var stored = List.copyOf(answers.values());
        var raw = observations.collectRaw(transientSession, stored);
        return new Result(summaries.summarize(session, stored),
                results.evaluate(session, q -> Optional.ofNullable(answers.get(q)), raw),
                products.match(session, q -> Optional.ofNullable(answers.get(q)), raw));
    }
}
