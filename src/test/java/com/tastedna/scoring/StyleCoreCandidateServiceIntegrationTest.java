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

// 실제 DB에 저장된 개발용 세션에서 Raw 관측과 초기 RESULT 후보 추출을 검증한다.
// 고정된 테스트 회원과 세션 데이터가 준비돼 있다는 전제에서 실행하는 통합 테스트다.
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

    // 저장된 세션의 Q02 선택이 R01과 R02 후보로 연결되는지 확인한다.
    // 실제 답변 저장 데이터에서 후보 추출까지 이어지는 흐름을 검증한다.
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
