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

// 실제 Spring 애플리케이션과 DB를 연결해 관측 생성 과정을 검증한다.
// 선택지 규칙 조회부터 저장된 답변의 Raw 관측 변환까지 확인하는 통합 테스트다.
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

    // DB에 등록된 Q01의 A·F 선택지 규칙을 조회해 직접 관측이 생성되는지 확인한다.
    // 선택 순서와 블랙·네이비 관측 내용이 올바르게 유지돼야 한다.
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

    // 테스트 세션과 Q01 답변을 DB에 저장한 뒤 Raw 관측으로 집계되는지 검증한다.
    // 트랜잭션 안에서 생성한 테스트 데이터를 사용해 기존 실제 세션과 구분한다.
    @Test
    @Transactional
    void aggregatesStoredQ01Answer() {

        String definitionId =
                "22222222-2222-4222-8222-222222222222";

        String sessionId = UUID.randomUUID().toString();
        String memberId = "raw-observation-test-member";

        // 무작위 세션 ID로 독립적인 테스트 세션을 생성한다.
        // 실제 사용자의 진행 중인 세션이나 기존 답변을 변경하지 않도록 분리한다.
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

        // Q01에서 A와 F를 선택한 답변 및 사용자에게 제시된 질문 정보를 저장한다.
        // 관측 수집 서비스가 저장된 답변을 해석하는 실제 경로를 검증하기 위한 데이터다.
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

        // 저장된 답변에서 직접 색상 선호 관측 두 개가 생성됐는지 확인한다.
        // 관측값뿐 아니라 원본 질문과 선택지 식별자도 유지되는지 검증한다.
        var colors = raw.get("DIRECT_COLOR_PREFERENCE");

        assertNotNull(colors);
        assertEquals(2, colors.size());

        assertEquals("블랙", colors.get(0).observedValue());
        assertEquals("네이비", colors.get(1).observedValue());
        assertEquals("Q01", colors.get(0).questionId());
        assertEquals("A", colors.get(0).optionId());
    }

}
