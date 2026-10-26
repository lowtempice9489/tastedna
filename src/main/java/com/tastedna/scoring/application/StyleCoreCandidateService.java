package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    // 실제 세션의 Raw 관측에서 RESULT 초기 후보와 근거 목록을 함께 추출한다.
    // 사용자 응답의 질문·선택지·관측 유형·Revision을 보존한다.
    public List<ResultCandidateEvidence> extractEvidenceFromSession(
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

            return StyleCoreCandidateExtractor.extractWithEvidence(
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

    // 실제 Simple 세션에서 추출한 후보별 근거를 판별 설정과 대조한다.
    // 현재는 승인된 후보 간 구별 규칙이 없어 최종 RESULT 확정을 보류한다.
    public SimpleCandidateDiscrimination assessDiscriminationFromSession(
            String memberId,
            String sessionId
    ) {
        try {
            var candidateEvidence = extractEvidenceFromSession(
                    memberId,
                    sessionId
            );

            JsonNode configuration = mapper.readTree(
                    Files.readString(Path.of(
                            "dev", "fixtures", "result",
                            "simple_style_core_discrimination_v0.1.json"
                    ))
            );

            return SimpleCandidateDiscrimination.assess(
                    candidateEvidence,
                    configuration
            );

        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "DISCRIMINATION_CONFIGURATION_LOAD_FAILED",
                    e
            );
        }
    }

    // 실제 세션에서 생성된 모든 Raw 관측을 Simple 판별용 프로필로 구성한다.
    // 개별 관측의 질문 ID, 선택지 ID, 관측 유형, 근거 및 Revision을 유지한다.
    public SimpleObservationProfile buildSimpleObservationProfile(
            String memberId,
            String sessionId
    ) {
        var rawObservations = observationService.collectRaw(
                memberId,
                sessionId
        );

        return new SimpleObservationProfile(
                rawObservations
        );
    }

    // 실제 Simple 세션의 전체 관측을 실험용 후보 속성 매핑과 비교한다.
    // 일치한 근거만 반환하며, 점수 계산이나 최종 RESULT 확정은 수행하지 않는다.
    public List<SimpleCandidateAttributeMatcher.CandidateMatches>
    matchSimpleAttributesFromSession(
            String memberId,
            String sessionId
    ) {
        SimpleObservationProfile profile =
                buildSimpleObservationProfile(
                        memberId,
                        sessionId
                );

        try {
            JsonNode mapping = mapper.readTree(
                    Files.readString(Path.of(
                            "dev", "fixtures", "result",
                            "simple_style_core_attribute_mapping_v0.1.json"
                    ))
            );

            // Q02에서 실제 선택한 초기 후보만 실험용 속성 매칭 대상에 포함한다.
            // 매핑 파일에 존재한다는 이유만으로 미선택 후보를 추가하지 않는다.
            Set<String> eligibleCandidates = extractFromSession(
                    memberId,
                    sessionId
            );

            return SimpleCandidateAttributeMatcher.match(
                            profile,
                            mapping
                    ).stream()
                    .filter(result -> eligibleCandidates.contains(
                            result.candidateId()
                    ))
                    .toList();

        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "ATTRIBUTE_MAPPING_LOAD_FAILED",
                    e
            );
        }
    }

}
