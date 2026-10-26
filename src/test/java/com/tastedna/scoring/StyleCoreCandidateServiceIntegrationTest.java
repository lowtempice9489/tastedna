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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // 실제 DB에 저장된 Q02 답변으로 후보별 근거가 생성되는지 검증한다.
    // 답변 Revision은 임의의 숫자가 아니라 저장된 Raw 관측값과 비교한다.
    @Test
    void extractsEvidenceFromActualSavedSession() {

        StyleCoreCandidateService service =
                new StyleCoreCandidateService(
                        observationService,
                        mapper
                );

        var evidence = service.extractEvidenceFromSession(
                MEMBER_ID,
                SESSION_ID
        );

        var rawObservations = observationService.collectRaw(
                MEMBER_ID,
                SESSION_ID
        );

        var rawQ02 = rawObservations.get(
                        "DIRECT_FASHION_LOOK_PREFERENCE"
                ).stream()
                .filter(raw -> "Q02".equals(raw.questionId()))
                .filter(raw -> "EXPLICIT_SELECTION".equals(raw.evidence()))
                .toList();

        assertEquals(2, evidence.size());

        assertEquals(
                Set.of("R01", "R02"),
                evidence.stream()
                        .map(result -> result.candidateId())
                        .collect(java.util.stream.Collectors.toSet())
        );

        for (var result : evidence) {
            assertEquals(1, result.sources().size());

            var source = result.sources().getFirst();

            assertEquals("Q02", source.questionId());
            assertEquals(result.candidateId(), source.optionId());
            assertEquals(
                    "DIRECT_FASHION_LOOK_PREFERENCE",
                    source.observationType()
            );
            assertEquals(
                    "EXPLICIT_SELECTION",
                    source.evidenceType()
            );

            assertTrue(
                    rawQ02.stream().anyMatch(raw ->
                            raw.optionId().equals(source.optionId())
                                    && raw.answerRevision()
                                    == source.answerRevision()
                    )
            );
        }
    }

    // 실제 Simple 세션에서 생성한 프로필이 원본 Raw 관측을 보존하는지 검증한다.
    // 관측되지 않은 질문을 임의로 추가하거나 취향 점수를 계산하지 않아야 한다.
    @Test
    void buildsSimpleObservationProfileFromSavedSession() {

        StyleCoreCandidateService service =
                new StyleCoreCandidateService(
                        observationService,
                        mapper
                );

        var profile = service.buildSimpleObservationProfile(
                MEMBER_ID,
                SESSION_ID
        );

        var rawObservations = observationService.collectRaw(
                MEMBER_ID,
                SESSION_ID
        );

        assertEquals(
                rawObservations,
                profile.observationsByType()
        );

        var expectedQuestionIds = rawObservations.values()
                .stream()
                .flatMap(java.util.List::stream)
                .map(raw -> raw.questionId())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(
                expectedQuestionIds,
                profile.observedQuestionIds()
        );

        assertEquals(
                rawObservations.getOrDefault(
                        "DIRECT_FASHION_LOOK_PREFERENCE",
                        java.util.List.of()
                ),
                profile.observationsOf(
                        "DIRECT_FASHION_LOOK_PREFERENCE"
                )
        );

        assertTrue(
                profile.observationsOf(
                        "NON_EXISTENT_OBSERVATION_TYPE"
                ).isEmpty()
        );
    }

    // 실제 Simple 세션의 속성 매칭 결과를 원본 Raw 관측과 대조한다.
    // 실험용 후보 매핑에 없는 선택이나 추론 관측은 일치 근거로 인정하지 않는다.
    @Test
    void matchesSimpleAttributesFromActualSavedSession() {

        StyleCoreCandidateService service =
                new StyleCoreCandidateService(
                        observationService,
                        mapper
                );

        var matches = service.matchSimpleAttributesFromSession(
                MEMBER_ID,
                SESSION_ID
        );

        var rawObservations = observationService.collectRaw(
                MEMBER_ID,
                SESSION_ID
        );

        assertEquals(
                Set.of("R01", "R02"),
                matches.stream()
                        .map(result -> result.candidateId())
                        .collect(java.util.stream.Collectors.toSet())
        );

        var allowedSelections = java.util.Map.of(
                "R01", Set.of("Q03:S02", "Q04:P01"),
                "R02", Set.of("Q03:S05", "Q04:P01")
        );

        for (var candidate : matches) {
            for (var match : candidate.matches()) {

                String selectionKey =
                        match.questionId() + ":" + match.optionId();

                assertTrue(
                        allowedSelections.get(
                                candidate.candidateId()
                        ).contains(selectionKey)
                );

                assertFalse(match.reason().isBlank());
                assertTrue(match.answerRevision() >= 1);

                assertTrue(
                        rawObservations.values().stream()
                                .flatMap(java.util.List::stream)
                                .anyMatch(raw ->
                                        raw.questionId().equals(
                                                match.questionId()
                                        )
                                                && raw.optionId().equals(
                                                match.optionId()
                                        )
                                                && "EXPLICIT_SELECTION".equals(
                                                raw.evidence()
                                        )
                                                && raw.answerRevision()
                                                == match.answerRevision()
                                )
                );
            }
        }
    }

    // 속성 매칭 결과에는 실제 Q02 초기 후보만 포함돼야 한다.
    // 실험용 매핑에 등록된 후보를 자동으로 추가하지 않는다.
    @Test
    void attributeMatchesContainOnlyActualInitialCandidates() {

        StyleCoreCandidateService service =
                new StyleCoreCandidateService(
                        observationService,
                        mapper
                );

        Set<String> initialCandidates = service.extractFromSession(
                MEMBER_ID,
                SESSION_ID
        );

        var matches = service.matchSimpleAttributesFromSession(
                MEMBER_ID,
                SESSION_ID
        );

        assertTrue(
                matches.stream()
                        .allMatch(result ->
                                initialCandidates.contains(
                                        result.candidateId()
                                )
                        )
        );

        assertEquals(
                Set.of("R01", "R02"),
                matches.stream()
                        .map(result -> result.candidateId())
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    // DB에 저장된 Simple 세션의 후보 매칭 결과가
    // 공통·차별 근거 비교까지 정상 연결되는지 확인한다.
    @Test
    void comparesCandidateEvidenceFromActualSavedSession() {

        StyleCoreCandidateService service =
                new StyleCoreCandidateService(
                        observationService,
                        mapper
                );

        var matches = service.matchSimpleAttributesFromSession(
                MEMBER_ID,
                SESSION_ID
        );

        var comparison =
                service.compareSimpleCandidateEvidenceFromSession(
                        MEMBER_ID,
                        SESSION_ID
                );

        var expected =
                com.tastedna.scoring.application
                        .SimpleCandidateAttributeMatcher
                        .compareEvidence(matches);

        assertEquals(
                expected,
                comparison
        );
    }

    // 실제 저장된 Simple 응답을 현재 실험용 판별 알고리즘에 적용한다.
    // 결과를 출력하여 공통 근거와 차별 근거의 실질적인 분포를 확인한다.
    @Test
    void inspectsActualSimpleAttributeSignals() {

        StyleCoreCandidateService service =
                new StyleCoreCandidateService(
                        observationService,
                        mapper
                );

        var assessment =
                service.assessSimpleAttributeSignalsFromSession(
                        MEMBER_ID,
                        SESSION_ID
                );

        System.out.println("===== SIMPLE SIGNAL INSPECTION =====");
        System.out.println(
                "Status: " + assessment.status()
        );
        System.out.println(
                "Provisional lead: "
                        + assessment.provisionalLeadCandidate()
        );
        System.out.println(
                "Common evidence: "
                        + assessment.evidence().commonSelections()
        );
        System.out.println(
                "Distinctive evidence: "
                        + assessment.evidence().distinctiveSelections()
        );
        System.out.println("====================================");

        assertTrue(
                java.util.Set.of(
                        "NO_DIFFERENTIATION",
                        "PROVISIONAL_LEAD",
                        "COMPETING_SIGNALS"
                ).contains(assessment.status())
        );

        var profile = service.buildSimpleObservationProfile(
                MEMBER_ID,
                SESSION_ID
        );

        System.out.println("===== SIMPLE RAW SELECTIONS =====");

        profile.observationsByType().forEach((type, observations) -> {
            observations.stream()
                    .filter(observation ->
                            "EXPLICIT_SELECTION".equals(
                                    observation.evidence()
                            )
                    )
                    .forEach(observation ->
                            System.out.println(
                                    observation.questionId()
                                            + " : "
                                            + observation.optionId()
                                            + " ["
                                            + type
                                            + "]"
                            )
                    );
        });

        System.out.println("===============================");

        // 실제 Simple 응답과 R01·R02 후보 프로필의 관계를 확인한다.
        var review = service.reviewSimpleCandidateProfilesFromSession(
                MEMBER_ID,
                SESSION_ID
        );

        System.out.println("===== SIMPLE CANDIDATE PROFILE REVIEW =====");
        System.out.println(
                "Shared support: " + review.sharedSupport()
        );
        System.out.println(
                "Compatible: " + review.compatible()
        );
        System.out.println(
                "Unmapped: " + review.unmapped()
        );
        System.out.println("==========================================");
    }

}
