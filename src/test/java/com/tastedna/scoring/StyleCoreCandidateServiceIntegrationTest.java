package com.tastedna.scoring;

import com.tastedna.scoring.application.SessionObservationService;
import com.tastedna.scoring.application.StyleCoreCandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class StyleCoreCandidateServiceIntegrationTest {

    private static final String MEMBER_ID =
            "dev-member-002";

    private static final String SESSION_ID =
            "b656c1a6-8013-49de-8e1a-29203e9800c1";

    @Autowired
    private SessionObservationService observationService;

    @Autowired
    private JsonMapper mapper;

    @Test
    void extractsCandidatesFromActualSavedSession() {

        StyleCoreCandidateService service =
                new StyleCoreCandidateService(
                        observationService,
                        mapper
                );

        Set<String> candidates =
                service.extractFromSession(
                        MEMBER_ID,
                        SESSION_ID
                );

        assertEquals(
                Set.of("R01", "R02"),
                candidates
        );
    }

    // 실제 세션의 초기 RESULT 후보와 미확정 시각적 근거를 함께 검증한다.
    // 이미지 정보가 준비되지 않은 상태에서는 최종 RESULT를 확정하지 않아야 한다.
    @Test
    void assessesActualSessionEvidenceReadiness() {

        StyleCoreCandidateService service =
                new StyleCoreCandidateService(
                        observationService,
                        mapper
                );

        var assessment = service.assessFromSession(
                MEMBER_ID,
                SESSION_ID
        );

        assertEquals(
                Set.of("R01", "R02"),
                assessment.eligibleCandidates()
        );

        assertEquals(
                Set.of("Q05", "Q08", "Q09", "Q10"),
                assessment.unavailableEvidenceQuestions()
        );

        assertFalse(assessment.finalResultReady());
    }

}
