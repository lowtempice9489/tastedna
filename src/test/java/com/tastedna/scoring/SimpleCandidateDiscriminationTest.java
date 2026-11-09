package com.tastedna.scoring;

import com.tastedna.scoring.application.RawObservationAggregator;
import com.tastedna.scoring.application.ResultCandidateEvidence;
import com.tastedna.scoring.application.SimpleCandidateDiscrimination;
import com.tastedna.scoring.application.SimpleObservationProfile;
import com.tastedna.scoring.application.SimpleInitialCandidateService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCandidateDiscriminationTest {

    @Test void candidateReviewSeparatesSourcesWithoutCountingDuplicatesOrInventingConflicts() {
        var q02 = new SimpleInitialCandidateService.Source("Q02", "스타일", "R01", "미니멀",
                "DIRECT_FASHION_LOOK_PREFERENCE", "EXPLICIT_SELECTION", 2);
        var q09 = new SimpleInitialCandidateService.Source("Q09", "비교", "C03:A", "미니멀 블랙 코디 그룹",
                "DIRECT_COMBINATION_PREFERENCE", "EXPLICIT_SELECTION", 4);
        String def = "33333333-3333-4333-8333-333333333333";
        var avoidance = new SimpleInitialCandidateService.Observation(def, "Q07", "A", null,
                "DIRECT_OUTFIT_AVOIDANCE", "착장 A", "EXPLICIT_SELECTION", 3, null);
        var candidate = new SimpleInitialCandidateService.Candidate("R01", "Minimal", List.of(q02),
                new SimpleInitialCandidateService.CandidateEvidence(List.of(q09, q02, q09), List.of(),
                        List.of(avoidance, avoidance), "CANDIDATE_RELATIONS_PENDING"));
        var response = reviewInput(List.of(candidate));
        var review = SimpleCandidateDiscrimination.reviewInitialCandidates(response);
        var evidence = review.candidates().getFirst();
        assertEquals(List.of(q02), evidence.directPreference());
        assertEquals(List.of(q09), evidence.additionalSupport());
        assertEquals(Set.of("Q02", "Q09"), evidence.supportingQuestions());
        assertEquals(List.of(avoidance), evidence.unresolved());
        assertTrue(evidence.approvedConflicts().isEmpty());
        assertFalse(evidence.missingRequirements().contains("CONFLICT_RESOLUTION_RULE_REQUIRED"));
        assertTrue(evidence.missingRequirements().contains("UNRESOLVED_OBSERVATION_RELATIONS"));
        assertTrue(review.validatedCandidateIds().isEmpty());
        assertEquals(response.definitionId(), review.definitionId());
        assertEquals(response.revision(), review.revision());
        assertEquals(review, SimpleCandidateDiscrimination.reviewInitialCandidates(response));
    }

    @Test void reviewWithoutQ02OrPositiveEvidenceNeverGrantsSufficiency() {
        var source = new SimpleInitialCandidateService.Source("Q10", "착장", "D", "착장 D",
                "DIRECT_FINAL_OUTFIT_PREFERENCE", "EXPLICIT_SELECTION", 5);
        var supported = new SimpleInitialCandidateService.Candidate("R13", "Graphic Street", List.of(),
                new SimpleInitialCandidateService.CandidateEvidence(List.of(source, source), List.of(), List.of(), "CANDIDATE_RELATIONS_PENDING"));
        var unsupported = new SimpleInitialCandidateService.Candidate("R18", "Minimal×Street", List.of(),
                new SimpleInitialCandidateService.CandidateEvidence(List.of(), List.of(), List.of(), "CANDIDATE_RELATIONS_PENDING"));
        var review = SimpleCandidateDiscrimination.reviewInitialCandidates(reviewInput(List.of(supported, unsupported)));
        assertEquals(List.of(source), review.candidates().getFirst().additionalSupport());
        assertEquals(Set.of("Q10"), review.candidates().getFirst().supportingQuestions());
        assertTrue(review.candidates().getFirst().directPreference().isEmpty());
        assertTrue(review.candidates().getLast().missingRequirements().contains("NO_APPROVED_POSITIVE_EVIDENCE"));
        assertTrue(review.validatedCandidateIds().isEmpty());
        assertFalse(review.missingRequirements().contains("CANDIDATE_LIMIT_POLICY_REQUIRED"));
    }

    SimpleInitialCandidateService.Response reviewInput(List<SimpleInitialCandidateService.Candidate> candidates) {
        return new SimpleInitialCandidateService.Response(new UUID(0, 1),
                UUID.fromString("33333333-3333-4333-8333-333333333333"), 10, "INITIAL_CANDIDATES", false,
                candidates, new SimpleInitialCandidateService.Decision("DEFERRED", List.of(), List.of()), null);
    }

    @Test void validatedSelectionIsRepeatableOrderIndependentAndWithinCandidates() {
        UUID session = new UUID(0, 1);
        var all = List.of("R01", "R07", "R13", "R16", "R17");
        for (int count = 1; count <= 5; count++) {
            var ids = all.subList(0, count);
            var selected = SimpleCandidateDiscrimination.selectValidatedRepresentative(session, 10, ids, "policy-v1");
            assertTrue(ids.contains(selected.orElseThrow()));
            var reordered = new ArrayList<>(ids);
            Collections.reverse(reordered);
            reordered.add(ids.getFirst());
            assertEquals(selected, SimpleCandidateDiscrimination.selectValidatedRepresentative(session, 10, reordered, "policy-v1"));
            assertEquals(selected, SimpleCandidateDiscrimination.selectValidatedRepresentative(session, 10, ids, "policy-v1"));
        }
        assertEquals("R25", SimpleCandidateDiscrimination.selectValidatedRepresentative(session, 10, List.of("R25"), "policy-v1").orElseThrow());
    }

    @Test void selectionEncodingHasStableGoldenVectorsForEveryInputDimension() {
        var ids = List.of("R01", "R07", "R13", "R16", "R17");
        // 독립 SHA-256 계산으로 고정한 벡터다. revision이 달라도 같은 ID로 귀결될 수 있다.
        assertEquals("R01", SimpleCandidateDiscrimination.selectValidatedRepresentative(new UUID(0, 1), 10, ids, "policy-v1").orElseThrow());
        assertEquals("R16", SimpleCandidateDiscrimination.selectValidatedRepresentative(new UUID(0, 2), 10, ids, "policy-v1").orElseThrow());
        assertEquals("R01", SimpleCandidateDiscrimination.selectValidatedRepresentative(new UUID(0, 1), 11, ids, "policy-v1").orElseThrow());
        assertEquals("R13", SimpleCandidateDiscrimination.selectValidatedRepresentative(new UUID(0, 1), 10, ids, "policy-v2").orElseThrow());
        assertEquals("R16", SimpleCandidateDiscrimination.selectValidatedRepresentative(new UUID(0, 1), 10,
                List.of("R01", "R07", "R13", "R16", "R25"), "policy-v1").orElseThrow());
    }

    @Test void emptyOversizedAndInvalidSelectionInputsDoNotSelect() {
        UUID session = new UUID(0, 1);
        assertTrue(SimpleCandidateDiscrimination.selectValidatedRepresentative(session, 1, List.of(), "v1").isEmpty());
        var error = assertThrows(IllegalArgumentException.class, () -> SimpleCandidateDiscrimination.selectValidatedRepresentative(
                session, 1, List.of("R01", "R02", "R03", "R04", "R05", "R06"), "v1"));
        assertEquals("CANDIDATE_LIMIT_POLICY_REQUIRED", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SimpleCandidateDiscrimination.selectValidatedRepresentative(session, 1, List.of("R26"), "v1"));
        assertThrows(IllegalArgumentException.class, () -> SimpleCandidateDiscrimination.selectValidatedRepresentative(session, 1, List.of("R01"), " "));
    }

    // R01과 R02가 모두 후보여도 구별 근거가 없으면 판별을 보류한다.
    @Test
    void keepsMultipleCandidatesUnresolved() {

        var result = SimpleCandidateDiscrimination.unresolved(
                Set.of("R01", "R02")
        );

        assertEquals(
                Set.of("R01", "R02"),
                result.eligibleCandidates()
        );

        assertNull(result.representativeCandidate());
        assertNull(result.finalResult());

        assertEquals(
                "INSUFFICIENT_DISCRIMINATING_EVIDENCE",
                result.reason()
        );
    }

    // 초기 후보가 하나뿐이어도 승인된 최종 판별 규칙 없이 확정하지 않는다.
    @Test
    void doesNotAutomaticallyFinalizeSingleCandidate() {

        var result = SimpleCandidateDiscrimination.unresolved(
                Set.of("R01")
        );

        assertEquals(
                Set.of("R01"),
                result.eligibleCandidates()
        );

        assertNull(result.representativeCandidate());
        assertNull(result.finalResult());
    }

    // 후보가 없는 경우에도 실제 관측되지 않은 RESULT를 만들어내지 않는다.
    @Test
    void doesNotInventCandidateWhenEvidenceIsAbsent() {

        var result = SimpleCandidateDiscrimination.unresolved(
                Set.of()
        );

        assertTrue(result.eligibleCandidates().isEmpty());
        assertNull(result.representativeCandidate());
        assertNull(result.finalResult());
    }

    // 생성 이후 원본 후보 집합이 변경돼도 판별 상태는 영향을 받지 않는다.
    @Test
    void protectsCandidateSetFromModification() {

        var candidates = new java.util.HashSet<>(
                Set.of("R01", "R02")
        );

        var result = SimpleCandidateDiscrimination.unresolved(
                candidates
        );

        candidates.add("R03");

        assertEquals(
                Set.of("R01", "R02"),
                result.eligibleCandidates()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.eligibleCandidates().add("R04")
        );
    }

    // 실제 개발용 판별 설정을 읽는다.
    private JsonNode loadDiscriminationConfig() throws Exception {
        return JsonMapper.builder().build().readTree(
                Files.readString(Path.of(
                        "dev", "fixtures", "result",
                        "simple_style_core_discrimination_v0.1.json"
                ))
        );
    }

    // 검증에 사용할 Q02 직접 선택 근거를 구성한다.
    private ResultCandidateEvidence evidenceFor(String candidateId) {
        return new ResultCandidateEvidence(
                candidateId,
                List.of(
                        new ResultCandidateEvidence.EvidenceSource(
                                "Q02",
                                candidateId,
                                "DIRECT_FASHION_LOOK_PREFERENCE",
                                "EXPLICIT_SELECTION",
                                3L
                        )
                )
        );
    }

    // R01·R02의 직접 관측이 유효해도 후보 간 구별 규칙이 미검증이면 확정하지 않는다.
    @Test
    void keepsVerifiedCandidatesUnresolved() throws Exception {

        var result = SimpleCandidateDiscrimination.assess(
                List.of(
                        evidenceFor("R01"),
                        evidenceFor("R02")
                ),
                loadDiscriminationConfig()
        );

        assertEquals(
                Set.of("R01", "R02"),
                result.eligibleCandidates()
        );

        assertNull(result.representativeCandidate());
        assertNull(result.finalResult());

        assertEquals(
                SimpleCandidateDiscrimination.INSUFFICIENT_EVIDENCE,
                result.reason()
        );
    }

    // 후보 설정에 없는 R03을 R01·R02 판별 대상으로 섞을 수 없다.
    @Test
    void rejectsCandidateOutsideConfiguredPair() throws Exception {

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> SimpleCandidateDiscrimination.assess(
                        List.of(evidenceFor("R03")),
                        loadDiscriminationConfig()
                )
        );

        assertEquals(
                "INVALID_DISCRIMINATION_EVIDENCE",
                exception.getMessage()
        );
    }

    // 미검증 상태가 아닌 설정이라도 지원되지 않는 판별 규칙을 임의 실행하지 않는다.
    @Test
    void rejectsUnsupportedDiscriminationStatus() throws Exception {

        JsonNode configuration = loadDiscriminationConfig();

        ((tools.jackson.databind.node.ObjectNode) configuration)
                .put("status", "VERIFIED");

        var exception = assertThrows(
                IllegalStateException.class,
                () -> SimpleCandidateDiscrimination.assess(
                        List.of(
                                evidenceFor("R01"),
                                evidenceFor("R02")
                        ),
                        configuration
                )
        );

        assertEquals(
                "DISCRIMINATION_RULE_NOT_SUPPORTED",
                exception.getMessage()
        );
    }


    // 순위형 선택과 그룹 내 비선호를 구분해 보존한다.
    // 이 관측만으로 최종 RESULT를 확정하지 않는다.
    @Test
    void keepsRankedPreferenceAndNonPreferenceSeparate() {
        String definitionId =
                "33333333-3333-4333-8333-333333333333";

        var preferred = new RawObservationAggregator.RawObservation(
                definitionId,
                "RANKED_COMPARISON_PREFERENCE",
                "1",
                "EXPLICIT_RANKED_SELECTION",
                "Q09",
                "C02:B",
                2L
        );

        var notPreferred = new RawObservationAggregator.RawObservation(
                definitionId,
                "RANKED_COMPARISON_NON_PREFERENCE",
                "NOT_PREFERRED",
                "UNSELECTED_IN_RANKED_GROUP",
                "Q09",
                "C02:C",
                2L
        );

        var profile = new SimpleObservationProfile(Map.of(
                "RANKED_COMPARISON_PREFERENCE",
                List.of(preferred),
                "RANKED_COMPARISON_NON_PREFERENCE",
                List.of(notPreferred)
        ));

        var review =
                new SimpleCandidateDiscrimination.ProfileEvidenceReview(
                        Map.of(),
                        Map.of(),
                        Map.of()
                );

        var readiness =
                SimpleCandidateDiscrimination.classifyEvidenceReadiness(
                        profile,
                        review
                );

        assertEquals(
                Set.of("Q09:C02:B"),
                readiness.get("RANKED_PREFERENCE_EVIDENCE")
        );

        assertEquals(
                Set.of("Q09:C02:C"),
                readiness.get("RANKED_NON_PREFERENCE_EVIDENCE")
        );

        assertTrue(
                readiness.get("OTHER_OBSERVATION").isEmpty()
        );

        var discrimination =
                SimpleCandidateDiscrimination.unresolved(Set.of("R01"));

        assertNull(discrimination.finalResult());
        assertNull(discrimination.representativeCandidate());
    }

}
