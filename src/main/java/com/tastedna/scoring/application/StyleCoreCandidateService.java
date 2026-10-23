package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class StyleCoreCandidateService {

    private final SessionObservationService observationService;
    private final JsonMapper mapper;

    public StyleCoreCandidateService(
            SessionObservationService observationService,
            JsonMapper mapper
    ) {
        this.observationService = observationService;
        this.mapper = mapper;
    }

    public Set<String> extractFromSession(
            String memberId,
            String sessionId
    ) {
        try {
            var rawObservations =
                    observationService.collectRaw(
                            memberId,
                            sessionId
                    );

            JsonNode observations = mapper.readTree(
                    mapper.writeValueAsString(rawObservations)
            );

            JsonNode mapping = mapper.readTree(
                    Files.readString(Path.of(
                            "dev", "fixtures", "result",
                            "style_core_direct_evidence_v0.1.json"
                    ))
            );

            return StyleCoreCandidateExtractor.extract(
                    observations,
                    mapping
            );

        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "CANDIDATE_MAPPING_LOAD_FAILED",
                    e
            );
        }
    }

    // 실제 세션에서 초기 RESULT 후보를 추출하고 시각적 근거의 준비 상태를 확인한다.
    // 메타데이터가 미확정인 문항은 후보 판별에 사용할 수 없는 근거로 분리한다.
    public ResultEvidenceAssessment assessFromSession(
            String memberId,
            String sessionId
    ) {
        Set<String> candidates = extractFromSession(
                memberId,
                sessionId
        );

        Set<String> unavailableQuestions =
                VisualEvidenceReadinessEvaluator
                        .findUnavailableQuestions(mapper);

        return new ResultEvidenceAssessment(
                candidates,
                unavailableQuestions,
                false
        );
    }

}
