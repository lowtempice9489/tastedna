package com.tastedna.demo;

import com.tastedna.scoring.application.SessionObservationService;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.application.multi.MultiChoiceAnswerPayloadExtractor;

import com.tastedna.session.domain.SessionAnswerId;
import com.tastedna.session.persistence.SessionAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

// Q02 직접 관측과 상품에 등록된 스타일만 대조하며 RESULT나 이미지로 스타일을 추론하지 않는다.
@Service
@RequiredArgsConstructor
public class DemoProductMatchService {
    private final TestSessionService sessions;
    private final SessionAnswerRepository answers;
    private final SessionObservationService observations;
    private final DemoProductRepository products;
    private final JsonMapper mapper;

    public record Evidence(String questionId, String optionId, String observationType, long answerRevision, String styleCode, String styleLabel) {}
    public record Product(String id, String name, String imageUrl, int price, boolean demo, List<Evidence> evidence) {}
    public record Response(UUID sessionId, UUID definitionId, long revision, String matchType, List<Product> products) {}

    @Transactional(readOnly = true)
    public Response match(String member, UUID id) {
        var session = sessions.get(member, id);
        if (!"SUBMITTED".equals(session.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NOT_SUBMITTED");
        if (!"SIMPLE".equals(session.mode().name()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SIMPLE_SESSION_REQUIRED");
        var answer = answers.findById(new SessionAnswerId(id.toString(), "Q02"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Q02_ANSWER_REQUIRED"));
        var question = mapper.readTree(answer.getPresentedPayload());
        var selected = MultiChoiceAnswerPayloadExtractor.extract(mapper.readTree(answer.getAnswerPayload()));
        Map<String, String> labels = new HashMap<>();
        for (var option : question.path("options"))
            labels.put(option.path("id").asString(), option.path("label").asString());
        Map<String, Evidence> evidence = new TreeMap<>();
        for (var observation : observations.collectRaw(member, id.toString())
                .getOrDefault("DIRECT_FASHION_LOOK_PREFERENCE", List.of())) {
            String code = observation.optionId();
            String label = STYLES.get(code);
            if (label != null && label.equals(labels.get(code)) && label.equals(observation.observedValue())
                    && session.definitionId().toString().equals(observation.definitionId())
                    && "Q02".equals(observation.questionId())
                    && "DIRECT_FASHION_LOOK_PREFERENCE".equals(observation.type())
                    && "EXPLICIT_SELECTION".equals(observation.evidence())
                    && answer.getAnswerRevision() == observation.answerRevision() && selected.contains(code))
                evidence.put(code, new Evidence("Q02", code, observation.type(), observation.answerRevision(), code, label));
        }
        List<Product> matched = evidence.isEmpty() ? List.of() : products.findMatchingStyles(evidence.keySet()).stream()
                .sorted(Comparator.comparing(DemoProduct::getProductId))
                .map(p -> new Product(p.getProductId(), p.getName(), p.getImageUrl(), p.getPrice(), true,
                        evidence.entrySet().stream().filter(e -> p.getStyleCodes().contains(e.getKey()))
                                .map(Map.Entry::getValue).toList()))
                .filter(p -> !p.evidence().isEmpty()).toList();
        return new Response(id, session.definitionId(), session.revision(), "EXPLICIT_STYLE_ATTRIBUTE", matched);
    }

    // V13 Q02의 승인된 코드·표시명이다. 유사 이름이나 RESULT 후보 판정으로 확장하지 않는다.
    private static final Map<String, String> STYLES = Map.ofEntries(
            Map.entry("R01", "미니멀"), Map.entry("R02", "모던"), Map.entry("R03", "클래식"),
            Map.entry("R04", "프레피"), Map.entry("R05", "캐주얼"), Map.entry("R06", "스포티"),
            Map.entry("R07", "스트릿"), Map.entry("R08", "빈티지·레트로"), Map.entry("R09", "로맨틱"),
            Map.entry("R10", "보헤미안"), Map.entry("R11", "에지·그런지"), Map.entry("R12", "글램·화려함"));
}