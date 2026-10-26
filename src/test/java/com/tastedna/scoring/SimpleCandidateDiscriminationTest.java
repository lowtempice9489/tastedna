package com.tastedna.scoring;

import com.tastedna.scoring.application.ResultCandidateEvidence;
import com.tastedna.scoring.application.SimpleCandidateDiscrimination;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCandidateDiscriminationTest {

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

}
