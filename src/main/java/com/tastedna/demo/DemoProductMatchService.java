package com.tastedna.demo;

import com.tastedna.scoring.application.SessionObservationService;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.application.multi.MultiChoiceAnswerPayloadExtractor;
import com.tastedna.session.application.single.SingleChoiceAnswerPayloadExtractor;
import com.tastedna.session.domain.SessionAnswerId;
import com.tastedna.session.persistence.SessionAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

// 직접 선택한 이미지의 동일성만 확인한다. RESULT 및 스타일 추론과 독립적이다.
@Service
@RequiredArgsConstructor
public class DemoProductMatchService {
    private final TestSessionService sessions;
    private final SessionAnswerRepository answers;
    private final SessionObservationService observations;
    private final DemoProductRepository products;
    private final JsonMapper mapper;

    public record Evidence(String questionId, String optionId, String observationType, long answerRevision) {}
    public record Product(String id, String name, String imageUrl, int price, boolean demo, List<Evidence> evidence) {}
    public record Response(UUID sessionId, UUID definitionId, long revision, String matchType, List<Product> products) {}

    @Transactional(readOnly = true)
    public Response match(String member, UUID id) {
        var session = sessions.get(member, id);
        if (!"SUBMITTED".equals(session.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NOT_SUBMITTED");
        if (!"SIMPLE".equals(session.mode().name()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SIMPLE_SESSION_REQUIRED");
        var answer = answers.findById(new SessionAnswerId(id.toString(), "Q06"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Q06_ANSWER_REQUIRED"));
        var question = mapper.readTree(answer.getPresentedPayload());
        var payload = mapper.readTree(answer.getAnswerPayload());
        List<String> selected = switch (question.path("kind").asString()) {
            case "SINGLE_CHOICE" -> List.of(SingleChoiceAnswerPayloadExtractor.extract(payload));
            case "MULTIPLE_CHOICE" -> MultiChoiceAnswerPayloadExtractor.extract(payload);
            default -> List.of();
        };
        Set<String> positive = new HashSet<>();
        for (var observation : observations.collectRaw(member, id.toString())
                .getOrDefault("DIRECT_CLOTHING_PREFERENCE", List.of())) {
            if (session.definitionId().toString().equals(observation.definitionId())
                    && "Q06".equals(observation.questionId())
                    && "DIRECT_CLOTHING_PREFERENCE".equals(observation.type())
                    && "EXPLICIT_SELECTION".equals(observation.evidence())
                    && answer.getAnswerRevision() == observation.answerRevision()
                    && selected.contains(observation.optionId())) positive.add(observation.optionId());
        }
        Map<String, List<Evidence>> byImage = new TreeMap<>();
        for (var option : question.path("options")) {
            String optionId = option.path("id").asString();
            if (!positive.contains(optionId)) continue;
            for (var image : option.path("images")) {
                if (image.isString() && !image.stringValue().isBlank())
                    byImage.computeIfAbsent(image.stringValue(), ignored -> new ArrayList<>())
                            .add(new Evidence("Q06", optionId, "DIRECT_CLOTHING_PREFERENCE", answer.getAnswerRevision()));
            }
        }
        List<Product> matched = byImage.isEmpty() ? List.of() : products.findByImageUrlInOrderByProductIdAsc(byImage.keySet()).stream()
                // MySQL collation이 대소문자를 무시해도 실제 경로는 정확히 일치해야 한다.
                .filter(p -> byImage.containsKey(p.getImageUrl()))
                .sorted(Comparator.comparing(DemoProduct::getProductId))
                .map(p -> new Product(p.getProductId(), p.getName(), p.getImageUrl(), p.getPrice(), true,
                        byImage.get(p.getImageUrl()).stream().distinct().sorted(Comparator.comparing(Evidence::optionId)).toList())).toList();
        return new Response(id, session.definitionId(), session.revision(), "EXACT_SELECTED_IMAGE", matched);
    }
}
