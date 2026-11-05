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
    final List<DemoProduct> catalog = new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        when(sessions.get("member", id)).thenReturn(new SessionResponse(id, Mode.SIMPLE, ApparelScope.mens, "SUBMITTED", 10, definition, null));
        var pattern = java.util.regex.Pattern.compile("VALUES \\('([^']+)','([^']+)','[^']+',(\\d+),'([^']+)'");
        var matcher = pattern.matcher(Files.readString(Path.of("src/main/resources/db/migration/V15__create_demo_store_catalog.sql")));
        while (matcher.find()) {
            var p = mock(DemoProduct.class);
            when(p.getProductId()).thenReturn(matcher.group(1));
            when(p.getName()).thenReturn(matcher.group(2));
            when(p.getPrice()).thenReturn(Integer.parseInt(matcher.group(3)));
            when(p.getImageUrl()).thenReturn(matcher.group(4));
            catalog.add(p);
        }
        assertEquals(7, catalog.size());
        when(products.findByImageUrlInOrderByProductIdAsc(any())).thenReturn(catalog);
        select("A", "DIRECT_CLOTHING_PREFERENCE");
    }
    void select(String option, String type) throws Exception {
        var q = ImageDefinitionTest.questions().get("Q06");
        when(answers.findById(new SessionAnswerId(id.toString(), "Q06"))).thenReturn(Optional.of(new SessionAnswer(
                id.toString(), "Q06", mapper.writeValueAsString(Map.of("selectedOptionId", option)), q.toString(), 6, LocalDateTime.now())));
        when(observations.collectRaw("member", id.toString())).thenReturn(Map.of(type, List.of(
                new RawObservationAggregator.RawObservation(definition.toString(), type, "의류 " + option,
                        "EXPLICIT_SELECTION", "Q06", option, 6))));
    }
    @Test void allSevenRealQ06ImagesMatchOnlyTheirV15Product() throws Exception {
        var sql = Files.readString(Path.of("src/main/resources/db/migration/V13__add_mens_image_simple_definition.sql"));
        for (int i = 0; i < 7; i++) {
            String option = String.valueOf((char) ('A' + i));
            assertTrue(sql.contains("'Q06','" + option + "','{}','{\"type\":\"DIRECT_CLOTHING_PREFERENCE\""));
            select(option, "DIRECT_CLOTHING_PREFERENCE");
            var result = service.match("member", id);
            assertEquals(result, service.match("member", id));
            assertEquals(1, result.products().size());
            var p = result.products().getFirst();
            assertEquals(catalog.get(i).getProductId(), p.id());
            assertEquals(catalog.get(i).getName(), p.name());
            assertEquals(catalog.get(i).getPrice(), p.price());
            assertEquals(catalog.get(i).getImageUrl(), p.imageUrl());
            assertTrue(p.demo());
            assertEquals("Q06", p.evidence().getFirst().questionId());
            assertEquals(option, p.evidence().getFirst().optionId());
        }
    }
    @Test void noProductOrOnlyCaseInsensitiveDatabaseMatchReturnsEmpty() {
        when(products.findByImageUrlInOrderByProductIdAsc(any())).thenReturn(List.of());
        assertTrue(service.match("member", id).products().isEmpty());
        var p = catalog.getFirst();
        String upperCaseImage = p.getImageUrl().toUpperCase(Locale.ROOT);
        when(p.getImageUrl()).thenReturn(upperCaseImage);
        when(products.findByImageUrlInOrderByProductIdAsc(any())).thenReturn(List.of(p));
        assertTrue(service.match("member", id).products().isEmpty());
    }
    @Test void avoidanceOrUnselectedObservationCannotMatch() throws Exception {
        select("A", "DIRECT_OUTFIT_AVOIDANCE");
        assertTrue(service.match("member", id).products().isEmpty());
        select("A", "DIRECT_CLOTHING_PREFERENCE");
        when(observations.collectRaw("member", id.toString())).thenReturn(Map.of("DIRECT_CLOTHING_PREFERENCE", List.of(
                new RawObservationAggregator.RawObservation(definition.toString(), "DIRECT_CLOTHING_PREFERENCE", "의류 B",
                        "EXPLICIT_SELECTION", "Q06", "B", 6))));
        assertTrue(service.match("member", id).products().isEmpty());
        verifyNoInteractions(products);
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
