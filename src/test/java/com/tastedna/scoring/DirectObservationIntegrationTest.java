package com.tastedna.scoring;

import com.tastedna.scoring.application.DirectObservationCollector;
import com.tastedna.scoring.application.SessionObservationService;
import com.tastedna.session.domain.SessionAnswer;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DirectObservationIntegrationTest {

    @Autowired
    private SessionAnswerRepository answerRepository;

    @Autowired
    private TestSessionRepository sessionRepository;

    @Autowired
    private SessionObservationService sessionObservationService;

    @Autowired
    private DirectObservationCollector collector;

    @Test
    void collectsActualQ01ObservationFromDatabase() {

        var observations = collector.collect(
                "22222222-2222-4222-8222-222222222222",
                "Q01",
                List.of("A", "F")
        );

        assertEquals(2, observations.size());

        assertEquals("A", observations.get(0).optionId());
        assertEquals("F", observations.get(1).optionId());

        assertTrue(
                observations.get(0)
                        .observationPayload()
                        .contains("블랙")
        );

        assertTrue(
                observations.get(1)
                        .observationPayload()
                        .contains("네이비")
        );
    }

    @Test
    @Transactional
    void aggregatesStoredQ01Answer() {

        String definitionId =
                "22222222-2222-4222-8222-222222222222";

        String sessionId = UUID.randomUUID().toString();
        String memberId = "raw-observation-test-member";

        var session = new com.tastedna.session.domain.TestSession(
                sessionId,
                memberId,
                definitionId,
                "MENS",
                "[\"Q01\"]",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(2)
        );

        sessionRepository.save(session);

        var answer = new SessionAnswer(
                sessionId,
                "Q01",
                "{\"selectedOptionIds\":[\"A\",\"F\"]}",
                """
                {
                  "kind": "MULTIPLE_CHOICE",
                  "options": [
                    {"id": "A", "label": "블랙"},
                    {"id": "F", "label": "네이비"}
                  ]
                }
                """,
                1L,
                LocalDateTime.now()
        );

        answerRepository.save(answer);

        var raw = sessionObservationService.collectRaw(
                memberId,
                sessionId
        );

        var colors = raw.get("DIRECT_COLOR_PREFERENCE");

        assertNotNull(colors);
        assertEquals(2, colors.size());

        assertEquals("블랙", colors.get(0).observedValue());
        assertEquals("네이비", colors.get(1).observedValue());
        assertEquals("Q01", colors.get(0).questionId());
        assertEquals("A", colors.get(0).optionId());
    }

}
