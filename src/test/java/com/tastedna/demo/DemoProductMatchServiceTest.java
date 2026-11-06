package com.tastedna.demo;

import com.tastedna.scoring.application.*;
import com.tastedna.session.ImageDefinitionTest;
import com.tastedna.session.api.dto.*;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.domain.*;
import com.tastedna.session.persistence.SessionAnswerRepository;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoProductMatchServiceTest {
    final TestSessionService sessions = mock(TestSessionService.class);
    final SessionAnswerRepository answers = mock(SessionAnswerRepository.class);
    final SessionObservationService observations = mock(SessionObservationService.class);
    final DemoProductRepository products = mock(DemoProductRepository.class);
    final JsonMapper mapper = JsonMapper.builder().build();
    final DemoProductMatchService service = new DemoProductMatchService(sessions, answers, observations, products, mapper);
    final UUID id = UUID.randomUUID();
    final UUID definition = UUID.fromString("33333333-3333-4333-8333-333333333333");
    @BeforeEach void setup() throws Exception {
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, ApparelScope.mens, "SUBMITTED", 10, definition, null));
        select("R01", "미니멀");
    }
    void select(String code, String label) throws Exception {
        var q = ImageDefinitionTest.questions().get("Q02");
        when(answers.findById(new SessionAnswerId(id.toString(), "Q02"))).thenReturn(Optional.of(new SessionAnswer(
                id.toString(), "Q02", mapper.writeValueAsString(Map.of("selectedOptionIds", List.of(code))), q.toString(), 2, LocalDateTime.now())));
        when(observations.collectRaw("member", id.toString())).thenReturn(Map.of("DIRECT_FASHION_LOOK_PREFERENCE", List.of(
                raw(code, label, definition.toString(), 2))));
    }
    RawObservationAggregator.RawObservation raw(String code, String label, String definitionId, long revision) {
        return new RawObservationAggregator.RawObservation(definitionId, "DIRECT_FASHION_LOOK_PREFERENCE", label,
                "EXPLICIT_SELECTION", "Q02", code, revision);
    }
    DemoProduct product(Set<String> codes) {
        var p = mock(DemoProduct.class);
        when(p.getProductId()).thenReturn("test-product");
        when(p.getName()).thenReturn("테스트 상품");
        when(p.getImageUrl()).thenReturn("/unrelated-product-image.png");
        when(p.getPrice()).thenReturn(10000);
        when(p.getStyleCodes()).thenReturn(codes);
        return p;
    }
    @Test void approvedVocabularyMatchesDifferentImagesAndIsReproducible() throws Exception {
        var q = ImageDefinitionTest.questions().get("Q02");
        int count = 0;
        for (var option : q.path("options")) {
            String code = option.path("id").asString(), label = option.path("label").asString();
            select(code, label);
            var registered = product(Set.of(code));
            when(products.findMatchingStyles(Set.of(code))).thenReturn(List.of(registered));
            var result = service.match("member", id);
            assertEquals(result, service.match("member", id));
            assertEquals("EXPLICIT_STYLE_ATTRIBUTE", result.matchType());
            var p = result.products().getFirst();
            assertEquals("/unrelated-product-image.png", p.imageUrl());
            assertEquals(10000, p.price());
            assertTrue(p.demo());
            assertEquals(List.of(new DemoProductMatchService.Evidence("Q02", code,
                    "DIRECT_FASHION_LOOK_PREFERENCE", 2, code, label)), p.evidence());
            count++;
        }
        assertEquals(12, count);
    }
    @Test void missingOrDifferentTagsCannotMatchEvenIfRepositoryReturnsThem() {
        var unmatched = List.of(product(Set.of()), product(Set.of("R02")), product(Set.of("r01")));
        when(products.findMatchingStyles(any())).thenReturn(unmatched);
        assertTrue(service.match("member", id).products().isEmpty());
        when(products.findMatchingStyles(any())).thenReturn(List.of());
        assertTrue(service.match("member", id).products().isEmpty());
    }
    @Test void evidenceMustMatchSelectionDefinitionRevisionAndExactLabel() throws Exception {
        for (var raw : List.of(raw("R02", "모던", definition.toString(), 2),
                raw("R01", "미니멀", UUID.randomUUID().toString(), 2),
                raw("R01", "미니멀", definition.toString(), 1),
                raw("R01", "미니멀룩", definition.toString(), 2))) {
            when(observations.collectRaw("member", id.toString())).thenReturn(Map.of("DIRECT_FASHION_LOOK_PREFERENCE", List.of(raw)));
            assertTrue(service.match("member", id).products().isEmpty());
        }
        when(observations.collectRaw("member", id.toString())).thenReturn(Map.of());
        assertTrue(service.match("member", id).products().isEmpty());
        verifyNoInteractions(products);
    }
    @Test void duplicateObservationsDoNotDuplicateEvidence() {
        var raw = raw("R01", "미니멀", definition.toString(), 2);
        when(observations.collectRaw("member", id.toString())).thenReturn(Map.of("DIRECT_FASHION_LOOK_PREFERENCE", List.of(raw, raw)));
        var registered = product(Set.of("R01", "R02"));
        when(products.findMatchingStyles(any())).thenReturn(List.of(registered));
        assertEquals(1, service.match("member", id).products().getFirst().evidence().size());
    }
    @Test void submissionModeAndOwnerAreCheckedBeforeAnswers() {
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, ApparelScope.mens, "IN_PROGRESS", 0, definition, null));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.match("member", id)).getStatusCode().value());
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.DEEP, ApparelScope.mens, "SUBMITTED", 0, definition, null));
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.match("member", id)).getStatusCode().value());
        when(sessions.get("other", id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.match("other", id)).getStatusCode().value());
        verifyNoInteractions(answers, observations, products);
    }
}
