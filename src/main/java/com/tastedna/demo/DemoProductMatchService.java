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
import java.util.function.Function;
import com.tastedna.session.domain.SessionAnswer;
import com.tastedna.session.api.dto.SessionResponse;
import com.tastedna.scoring.application.RawObservationAggregator;

// Q02 직접 관측과 상품에 등록된 스타일만 대조하며 RESULT나 이미지로 스타일을 추론하지 않는다.
@Service
@RequiredArgsConstructor
public class DemoProductMatchService {
    private final TestSessionService sessions;
    private final SessionAnswerRepository answers;
    private final SessionObservationService observations;
    private final DemoProductRepository products;
    private final JsonMapper mapper;

    public record Evidence(String questionId, String optionId, String observationType, long answerRevision,
                           String styleCode, String styleLabel, String colorCode, String colorLabel) {
        public Evidence(String questionId, String optionId, String observationType, long answerRevision, String styleCode, String styleLabel) {
            this(questionId, optionId, observationType, answerRevision, styleCode, styleLabel, null, null);
        }
    }
    public record Product(String id, String name, String imageUrl, int price, boolean demo, List<Evidence> evidence) {}
    public record Response(UUID sessionId, UUID definitionId, long revision, String matchType, List<Product> products) {}

    @Transactional(readOnly = true)
    public Response match(String member, UUID id) {
        var session = sessions.get(member, id);
        if (!"SUBMITTED".equals(session.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NOT_SUBMITTED");
        if (!"SIMPLE".equals(session.mode().name()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SIMPLE_SESSION_REQUIRED");
        return match(session, questionId -> answers.findById(new SessionAnswerId(id.toString(), questionId)),
                observations.collectRaw(member, id.toString()));
    }

    public Response match(SessionResponse session, Function<String, Optional<SessionAnswer>> lookup,
            Map<String, List<RawObservationAggregator.RawObservation>> raw) {
        UUID id = session.sessionId();
        if (!"SUBMITTED".equals(session.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NOT_SUBMITTED");
        if (!"SIMPLE".equals(session.mode().name()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SIMPLE_SESSION_REQUIRED");
        var answer = lookup.apply("Q02")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Q02_ANSWER_REQUIRED"));
        var question = mapper.readTree(answer.getPresentedPayload());
        var selected = MultiChoiceAnswerPayloadExtractor.extract(mapper.readTree(answer.getAnswerPayload()));
        Map<String, String> labels = new HashMap<>();
        for (var option : question.path("options"))
            labels.put(option.path("id").asString(), option.path("label").asString());
        Map<String, Evidence> evidence = new TreeMap<>();
        for (var observation : raw
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
        Map<String, Evidence> colors = new TreeMap<>();
        var colorAnswer = lookup.apply("Q01");
        if (colorAnswer.isPresent()) {
            var saved = colorAnswer.get();
            var colorSelected = MultiChoiceAnswerPayloadExtractor.extract(mapper.readTree(saved.getAnswerPayload()));
            Map<String, String> colorLabels = new HashMap<>();
            for (var option : mapper.readTree(saved.getPresentedPayload()).path("options"))
                colorLabels.put(option.path("id").asString(), option.path("label").asString());
            for (var observation : raw.getOrDefault("DIRECT_COLOR_PREFERENCE", List.of())) {
                String code = observation.optionId(), label = COLORS.get(code);
                if (label != null && label.equals(colorLabels.get(code)) && label.equals(observation.observedValue())
                        && session.definitionId().toString().equals(observation.definitionId())
                        && "Q01".equals(observation.questionId()) && "DIRECT_COLOR_PREFERENCE".equals(observation.type())
                        && "EXPLICIT_SELECTION".equals(observation.evidence())
                        && saved.getAnswerRevision() == observation.answerRevision() && colorSelected.contains(code))
                    colors.put(code, new Evidence("Q01", code, observation.type(), observation.answerRevision(), null, null, code, label));
            }
        }
        List<Product> matched = evidence.isEmpty() ? List.of() : products.findMatchingStyles(evidence.keySet()).stream()
                .filter(p -> session.apparelScope() != null && p.getApparelScope() != null
                        && (p.getApparelScope() == DemoProduct.ApparelScope.UNISEX
                        || p.getApparelScope().name().equals(session.apparelScope().name().toUpperCase(Locale.ROOT))))
                .sorted(Comparator.comparing(DemoProduct::getProductId))
                .filter(p -> p.getStyleCodes().stream().anyMatch(evidence::containsKey))
                .map(p -> {
                    var matchedEvidence = new ArrayList<>(evidence.entrySet().stream()
                            .filter(e -> p.getStyleCodes().contains(e.getKey())).map(Map.Entry::getValue).toList());
                    // 색상 근거는 후보를 제외하지 않고 우선 배치 여부에만 사용한다.
                    colors.forEach((code, fact) -> { if (p.getColorCodes().contains(code)) matchedEvidence.add(fact); });
                    return new Product(p.getProductId(), p.getName(), p.getImageUrl(), p.getPrice(), true, List.copyOf(matchedEvidence));
                })
                // 안정 정렬로 색상 일치 상품을 먼저 두고, 그룹 안의 기존 상품 ID 순서는 보존한다.
                .sorted(Comparator.comparing((Product p) -> p.evidence().stream()
                        .noneMatch(e -> "Q01".equals(e.questionId()) && e.colorCode() != null)))
                .toList();
        return new Response(id, session.definitionId(), session.revision(), "EXPLICIT_STYLE_ATTRIBUTE", matched);
    }

    // V13 Q02의 승인된 코드·표시명이다. 유사 이름이나 RESULT 후보 판정으로 확장하지 않는다.
    private static final Map<String, String> STYLES = Map.ofEntries(
            Map.entry("R01", "미니멀"), Map.entry("R02", "모던"), Map.entry("R03", "클래식"),
            Map.entry("R04", "프레피"), Map.entry("R05", "캐주얼"), Map.entry("R06", "스포티"),
            Map.entry("R07", "스트릿"), Map.entry("R08", "빈티지·레트로"), Map.entry("R09", "로맨틱"),
            Map.entry("R10", "보헤미안"), Map.entry("R11", "에지·그런지"), Map.entry("R12", "글램·화려함"));
    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("A", "블랙"), Map.entry("B", "화이트·아이보리"), Map.entry("C", "그레이·실버"),
            Map.entry("D", "베이지·크림"), Map.entry("E", "브라운·카멜"), Map.entry("F", "네이비"),
            Map.entry("G", "블루"), Map.entry("H", "그린·올리브"), Map.entry("I", "레드·버건디"),
            Map.entry("J", "핑크"), Map.entry("K", "퍼플"), Map.entry("L", "옐로·머스터드"),
            Map.entry("M", "오렌지·코랄"), Map.entry("N", "파스텔 계열"), Map.entry("O", "선명하고 밝은 색 계열"));
}
