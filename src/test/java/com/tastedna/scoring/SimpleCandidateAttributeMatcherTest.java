package com.tastedna.scoring;

import com.tastedna.scoring.application.RawObservationAggregator;
import com.tastedna.scoring.application.SimpleCandidateAttributeMatcher;
import com.tastedna.scoring.application.SimpleObservationProfile;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCandidateAttributeMatcherTest {

    private static final String DEFINITION_ID =
            "22222222-2222-4222-8222-222222222222";

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    private JsonNode loadMapping() throws Exception {
        return mapper.readTree(
                Files.readString(Path.of(
                        "dev", "fixtures", "result",
                        "simple_style_core_attribute_mapping_v0.1.json"
                ))
        );
    }

    private RawObservationAggregator.RawObservation observation(
            String type,
            String questionId,
            String optionId,
            long revision
    ) {
        return new RawObservationAggregator.RawObservation(
                DEFINITION_ID,
                type,
                optionId,
                "EXPLICIT_SELECTION",
                questionId,
                optionId,
                revision
        );
    }

    // Q03·Q04의 실제 선택을 후보별 속성 매핑과 비교한다.
    // 공통 속성이 일치하더라도 후보를 자동 확정하지 않는다.
    @Test
    void matchesExplicitAttributesForBothCandidates()
            throws Exception {

        var profile = new SimpleObservationProfile(
                Map.of(
                        "DIRECT_SILHOUETTE_PREFERENCE",
                        List.of(observation(
                                "DIRECT_SILHOUETTE_PREFERENCE",
                                "Q03", "S02", 2L
                        )),
                        "DIRECT_PATTERN_GRAPHIC_PREFERENCE",
                        List.of(observation(
                                "DIRECT_PATTERN_GRAPHIC_PREFERENCE",
                                "Q04", "P01", 4L
                        ))
                )
        );

        var results = SimpleCandidateAttributeMatcher.match(
                profile,
                loadMapping()
        );

        var byCandidate = results.stream().collect(
                Collectors.toMap(
                        SimpleCandidateAttributeMatcher.CandidateMatches
                                ::candidateId,
                        result -> result
                )
        );

        assertEquals(Set.of("R01", "R02"), byCandidate.keySet());

        assertEquals(
                Set.of("S02", "P01"),
                byCandidate.get("R01").matches().stream()
                        .map(match -> match.optionId())
                        .collect(Collectors.toSet())
        );

        assertEquals(
                List.of("P01"),
                byCandidate.get("R02").matches().stream()
                        .map(match -> match.optionId())
                        .toList()
        );

        assertEquals(
                2L,
                byCandidate.get("R01").matches().stream()
                        .filter(match -> "S02".equals(match.optionId()))
                        .findFirst()
                        .orElseThrow()
                        .answerRevision()
        );

        assertEquals(
                4L,
                byCandidate.get("R02").matches().getFirst()
                        .answerRevision()
        );
    }

    // 관측되지 않은 속성은 일치 결과에 포함하지 않는다.
    @Test
    void doesNotMatchUnselectedAttributes() throws Exception {

        var profile = new SimpleObservationProfile(
                Map.of(
                        "DIRECT_SILHOUETTE_PREFERENCE",
                        List.of(observation(
                                "DIRECT_SILHOUETTE_PREFERENCE",
                                "Q03", "S04", 1L
                        ))
                )
        );

        var results = SimpleCandidateAttributeMatcher.match(
                profile,
                loadMapping()
        );

        assertTrue(
                results.stream().allMatch(
                        result -> result.matches().isEmpty()
                )
        );
    }

    // 직접 선택이 아닌 추론 관측은 실험용 속성 매칭에 반영하지 않는다.
    @Test
    void ignoresInferredObservations() throws Exception {

        var inferred = new RawObservationAggregator.RawObservation(
                DEFINITION_ID,
                "DIRECT_PATTERN_GRAPHIC_PREFERENCE",
                "무지·솔리드",
                "INFERRED",
                "Q04",
                "P01",
                2L
        );

        var profile = new SimpleObservationProfile(
                Map.of(
                        "DIRECT_PATTERN_GRAPHIC_PREFERENCE",
                        List.of(inferred)
                )
        );

        var results = SimpleCandidateAttributeMatcher.match(
                profile,
                loadMapping()
        );

        assertTrue(
                results.stream().allMatch(
                        result -> result.matches().isEmpty()
                )
        );
    }

    // 최종 RESULT용으로 승인되지 않은 설정은 실험용 매칭에서 사용하지 않는다.
    @Test
    void rejectsNonExperimentalMapping() throws Exception {

        JsonNode mapping = loadMapping();

        ((tools.jackson.databind.node.ObjectNode) mapping)
                .put("status", "APPROVED");

        var profile = new SimpleObservationProfile(Map.of());

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> SimpleCandidateAttributeMatcher.match(
                        profile, mapping
                )
        );

        assertEquals(
                "INVALID_ATTRIBUTE_MAPPING",
                exception.getMessage()
        );
    }

    // 두 후보가 공유하는 선택과 각 후보에만 연결된 선택을 구분한다.
    @Test
    void separatesCommonAndDistinctiveEvidence() {

        var matches = java.util.List.of(
                new SimpleCandidateAttributeMatcher.CandidateMatches(
                        "R01",
                        java.util.List.of(
                                new SimpleCandidateAttributeMatcher.AttributeMatch(
                                        "Q03", "S02", "R01 속성 가설", 1L
                                ),
                                new SimpleCandidateAttributeMatcher.AttributeMatch(
                                        "Q04", "P01", "공통 속성 가설", 1L
                                )
                        )
                ),
                new SimpleCandidateAttributeMatcher.CandidateMatches(
                        "R02",
                        java.util.List.of(
                                new SimpleCandidateAttributeMatcher.AttributeMatch(
                                        "Q03", "S05", "R02 속성 가설", 1L
                                ),
                                new SimpleCandidateAttributeMatcher.AttributeMatch(
                                        "Q04", "P01", "공통 속성 가설", 1L
                                )
                        )
                )
        );

        var comparison =
                SimpleCandidateAttributeMatcher.compareEvidence(
                        matches
                );

        assertEquals(
                java.util.Set.of("Q04:P01"),
                comparison.commonSelections()
        );

        assertEquals(
                java.util.Set.of("Q03:S02"),
                comparison.distinctiveSelections().get("R01")
        );

        assertEquals(
                java.util.Set.of("Q03:S05"),
                comparison.distinctiveSelections().get("R02")
        );
    }

}
