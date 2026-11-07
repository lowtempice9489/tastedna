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
    @Test void colorsPrioritizeMatchesWithoutRemovingOtherStyleCandidates() throws Exception {
        var q = ImageDefinitionTest.questions().get("Q01");
        when(answers.findById(new SessionAnswerId(id.toString(), "Q01"))).thenReturn(Optional.of(new SessionAnswer(
                id.toString(), "Q01", "{\"selectedOptionIds\":[\"B\",\"A\"]}", q.toString(), 3, LocalDateTime.now())));
        var color = new RawObservationAggregator.RawObservation(definition.toString(), "DIRECT_COLOR_PREFERENCE",
                "블랙", "EXPLICIT_SELECTION", "Q01", "A", 3);
        var wrongRevision = new RawObservationAggregator.RawObservation(definition.toString(), "DIRECT_COLOR_PREFERENCE",
                "화이트·아이보리", "EXPLICIT_SELECTION", "Q01", "B", 2);
        var wrongDefinition = new RawObservationAggregator.RawObservation(UUID.randomUUID().toString(), "DIRECT_COLOR_PREFERENCE",
                "화이트·아이보리", "EXPLICIT_SELECTION", "Q01", "B", 3);
        when(observations.collectRaw("member", id.toString())).thenReturn(Map.of(
                "DIRECT_FASHION_LOOK_PREFERENCE", List.of(raw("R01", "미니멀", definition.toString(), 2)),
                "DIRECT_COLOR_PREFERENCE", List.of(color, color, wrongRevision, wrongDefinition)));
        var both = product(Set.of("R01"));
        var different = product(Set.of("R01"));
        var missing = product(Set.of("R01"));
        var colorOnly = product(Set.of("R02"));
        when(both.getProductId()).thenReturn("z");when(both.getColorCodes()).thenReturn(Set.of("A", "B"));
        when(different.getProductId()).thenReturn("b");when(different.getColorCodes()).thenReturn(Set.of("G"));
        when(missing.getProductId()).thenReturn("c");
        when(colorOnly.getColorCodes()).thenReturn(Set.of("A"));
        when(products.findMatchingStyles(any())).thenReturn(List.of(missing, different, colorOnly, both));
        var result = service.match("member", id);
        assertEquals(List.of("z", "b", "c"), result.products().stream().map(DemoProductMatchService.Product::id).toList());
        assertEquals(2, result.products().getFirst().evidence().size());
        assertEquals("A", result.products().getFirst().evidence().get(1).colorCode());
        assertNull(result.products().getFirst().evidence().get(1).styleCode());
        assertEquals(1, result.products().get(1).evidence().size());
        assertEquals(1, result.products().get(2).evidence().size());
        assertEquals(result, service.match("member", id));
        verify(observations, times(2)).collectRaw("member", id.toString());
    }
    @Test void colorGroupsKeepIdOrderRegardlessOfMatchCountAndMissingAnswerRestoresOldOrder() throws Exception {
        var q = ImageDefinitionTest.questions().get("Q01");
        when(answers.findById(new SessionAnswerId(id.toString(), "Q01"))).thenReturn(Optional.of(new SessionAnswer(
                id.toString(), "Q01", "{\"selectedOptionIds\":[\"B\",\"A\"]}", q.toString(), 3, LocalDateTime.now())));
        when(observations.collectRaw("member", id.toString())).thenReturn(Map.of(
                "DIRECT_FASHION_LOOK_PREFERENCE", List.of(raw("R01", "미니멀", definition.toString(), 2)),
                "DIRECT_COLOR_PREFERENCE", List.of(
                        new RawObservationAggregator.RawObservation(definition.toString(), "DIRECT_COLOR_PREFERENCE", "블랙", "EXPLICIT_SELECTION", "Q01", "A", 3),
                        new RawObservationAggregator.RawObservation(definition.toString(), "DIRECT_COLOR_PREFERENCE", "화이트·아이보리", "EXPLICIT_SELECTION", "Q01", "B", 3))));
        var one = product(Set.of("R01"));
        var two = product(Set.of("R01"));
        var missing = product(Set.of("R01"));
        var different = product(Set.of("R01"));
        when(one.getProductId()).thenReturn("c"); when(one.getColorCodes()).thenReturn(Set.of("A"));
        when(two.getProductId()).thenReturn("d"); when(two.getColorCodes()).thenReturn(Set.of("A", "B"));
        when(missing.getProductId()).thenReturn("a");
        when(different.getProductId()).thenReturn("b"); when(different.getColorCodes()).thenReturn(Set.of("G"));
        when(products.findMatchingStyles(any())).thenReturn(List.of(two, different, missing, one));
        var result = service.match("member", id);
        assertEquals(List.of("c", "d", "a", "b"), result.products().stream().map(DemoProductMatchService.Product::id).toList());
        assertEquals(result, service.match("member", id));
        when(answers.findById(new SessionAnswerId(id.toString(), "Q01"))).thenReturn(Optional.empty());
        var withoutColorAnswer = service.match("member", id);
        assertEquals(List.of("a", "b", "c", "d"), withoutColorAnswer.products().stream().map(DemoProductMatchService.Product::id).toList());
        assertEquals(result.products().size(), withoutColorAnswer.products().size());
    }
    @Test void registeredCatalogMatchesOnlySessionScopeAndSelectedRawStyle() throws Exception {
        var catalog = DemoCatalogTest.scopedCatalog();
        when(products.findMatchingStyles(any())).thenReturn(catalog);
        for (var scope : ApparelScope.values()) {
            when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, scope, "SUBMITTED", 10, definition, null));
            var reachable = new HashSet<String>();
            for (var option : ImageDefinitionTest.questions().get("Q02").path("options")) {
                String code = option.path("id").asString();
                select(code, option.path("label").asString());
                var expected = catalog.stream()
                        .filter(p -> p.getApparelScope().name().equalsIgnoreCase(scope.name()))
                        .filter(p -> p.getStyleCodes().contains(code))
                        .map(DemoProduct::getProductId).sorted().toList();
                var result = service.match("member", id);
                assertEquals(expected, result.products().stream().map(DemoProductMatchService.Product::id).toList());
                assertTrue(result.products().stream().allMatch(p -> p.evidence().stream()
                        .allMatch(e -> e.questionId().equals("Q02") && e.optionId().equals(code) && e.styleCode().equals(code))));
                assertEquals(result, service.match("member", id));
                reachable.addAll(expected);
            }
            assertEquals(catalog.stream().filter(p -> p.getApparelScope().name().equalsIgnoreCase(scope.name()))
                    .filter(p -> !p.getStyleCodes().isEmpty()).count(), reachable.size());
            assertFalse(reachable.isEmpty());
        }
    }
    @Test void existingCatalogWithoutExplicitScopeCannotMatch() throws Exception {
        var catalog = DemoCatalogTest.catalog(true);
        assertEquals(100, catalog.size());
        assertTrue(catalog.stream().allMatch(p -> p.getApparelScope() == null));
        when(products.findMatchingStyles(any())).thenReturn(catalog);
        for (var scope : ApparelScope.values()) {
            when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, scope, "SUBMITTED", 10, definition, null));
            for (var option : ImageDefinitionTest.questions().get("Q02").path("options")) {
                select(option.path("id").asString(), option.path("label").asString());
                assertTrue(service.match("member", id).products().isEmpty());
            }
        }
    }
    @Test void apparelScopeAllowsOnlyMatchingOrUnisexProductsWithMatchingStyle() {
        var mens = product(Set.of("R01"));
        var womens = product(Set.of("R01"));
        var unisex = product(Set.of("R01"));
        var unknown = product(Set.of("R01"));
        var wrongStyle = product(Set.of("R02"));
        when(mens.getProductId()).thenReturn("mens");
        when(womens.getProductId()).thenReturn("womens");
        when(unisex.getProductId()).thenReturn("unisex");
        when(womens.getApparelScope()).thenReturn(DemoProduct.ApparelScope.WOMENS);
        when(unisex.getApparelScope()).thenReturn(DemoProduct.ApparelScope.UNISEX);
        when(unknown.getApparelScope()).thenReturn(null);
        when(wrongStyle.getApparelScope()).thenReturn(DemoProduct.ApparelScope.UNISEX);
        when(products.findMatchingStyles(any())).thenReturn(List.of(mens, womens, unisex, unknown, wrongStyle));
        assertEquals(List.of("mens", "unisex"), service.match("member", id).products().stream().map(DemoProductMatchService.Product::id).toList());
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, ApparelScope.womens, "SUBMITTED", 10, definition, null));
        assertEquals(List.of("unisex", "womens"), service.match("member", id).products().stream().map(DemoProductMatchService.Product::id).toList());
        assertEquals(service.match("member", id), service.match("member", id));
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, null, "SUBMITTED", 10, definition, null));
        assertTrue(service.match("member", id).products().isEmpty());
    }
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
        when(p.getApparelScope()).thenReturn(DemoProduct.ApparelScope.MENS);
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
