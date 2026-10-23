package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

// 실제 세션의 Raw 관측을 이용해 Style Core 초기 후보와 근거 준비 상태를 평가한다.
// 후보 추출과 메타데이터 검증을 연결하지만 최종 RESULT를 결정하지는 않는다.
public class StyleCoreCandidateService {

    private final SessionObservationService observationService;
    private final JsonMapper mapper;

    // 세션 관측 서비스와 JSON 변환 도구를 전달받아 사용한다.
    // 관측 생성 로직을 중복 구현하지 않고 기존 서비스를 재사용한다.
    public StyleCoreCandidateService(
            SessionObservationService observationService,
            JsonMapper mapper
    ) {
        this.observationService = observationService;
        this.mapper = mapper;
    }

    // 회원의 실제 세션에서 Raw 관측을 수집하고 매핑 규칙에 따라 초기 후보를 추출한다.
    // 현재는 개발용 fixtures 파일을 읽으므로 운영 배포 시 설정 관리 방식을 변경해야 한다.
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

            // 관측 서비스의 Map 결과를 후보 추출기가 사용하는 JsonNode 형태로 변환한다.
            // 이 과정에서 후보 점수나 새로운 취향 정보를 생성하지 않는다.
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

    // 실제 세션에서 초기 후보를 추출하고 Q05·Q08·Q09·Q10의 메타데이터 준비 상태를 확인한다.
    // 현재는 근거 준비 여부만 반환하며, 최종 RESULT 판별 가능 상태는 false로 유지한다.
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
