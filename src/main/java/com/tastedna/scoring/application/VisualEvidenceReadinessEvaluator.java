package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public final class VisualEvidenceReadinessEvaluator {

    private VisualEvidenceReadinessEvaluator() {
    }

    // 실제 착장 메타데이터를 읽고 문항별 근거 사용 준비 상태를 확인한다.
    // 미확정된 선택지가 있으면 해당 문항을 사용 불가능 목록에 포함한다.
    public static Set<String> findUnavailableQuestions(
            JsonMapper mapper
    ) {
        try {
            Set<String> unavailable = new LinkedHashSet<>();

            JsonNode outfitMetadata = load(
                    mapper,
                    "simple_q05_q10_outfit_metadata_v0.1.json"
            );

            for (String questionId : new String[]{"Q05", "Q10"}) {
                JsonNode question = outfitMetadata
                        .path("questions")
                        .path(questionId);

                if (!allOptionsEligible(question.path("options"))) {
                    unavailable.add(questionId);
                }
            }

            checkComparison(
                    mapper,
                    "Q08",
                    "simple_q08_comparison_metadata_v0.1.json",
                    unavailable
            );

            checkComparison(
                    mapper,
                    "Q09",
                    "simple_q09_combination_metadata_v0.1.json",
                    unavailable
            );

            return Set.copyOf(unavailable);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "VISUAL_METADATA_LOAD_FAILED",
                    e
            );
        }
    }

    // 비교형 문항은 모든 비교쌍의 A/B 선택지가 준비돼야 한다.
    // 한 비교쌍이라도 미확정이면 문항 전체를 미사용 상태로 판단한다.
    private static void checkComparison(
            JsonMapper mapper,
            String questionId,
            String filename,
            Set<String> unavailable
    ) throws IOException {

        JsonNode metadata = load(mapper, filename);
        JsonNode comparisons = metadata.path("comparisons");

        if (!comparisons.isArray() || comparisons.isEmpty()) {
            unavailable.add(questionId);
            return;
        }

        for (JsonNode comparison : comparisons) {
            if (!allOptionsEligible(comparison.path("options"))) {
                unavailable.add(questionId);
                return;
            }
        }
    }

    // 선택지별 이미지 ID와 검증된 RESULT 연결 정보가 있는지 확인한다.
    // 이 검사는 메타데이터의 형식적 준비 상태만 판단한다.
    private static boolean allOptionsEligible(
            JsonNode options
    ) {
        if (!options.isArray() || options.isEmpty()) {
            return false;
        }

        for (JsonNode option : options) {
            if (!VisualEvidenceEligibility.isEligible(
                    option,
                    option.path("candidateEvidenceMappings")
            )) {
                return false;
            }
        }

        return true;
    }

    private static JsonNode load(
            JsonMapper mapper,
            String filename
    ) throws IOException {

        return mapper.readTree(
                Files.readString(Path.of(
                        "dev", "fixtures", "result", filename
                ))
        );
    }
}
