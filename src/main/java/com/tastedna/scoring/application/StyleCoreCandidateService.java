package com.tastedna.scoring.application;

import com.tastedna.scoring.persistence.StyleCoreDirectMappingRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 실제 세션의 Raw 관측을 이용해 Style Core 초기 후보와 근거 준비 상태를 평가한다.
// Spring Bean으로 관리하며, 최종 종합 RESULT를 확정하지는 않는다.
@Service
public class StyleCoreCandidateService {

    private final SessionObservationService observationService;
    private final JsonMapper mapper;
    private final StyleCoreDirectMappingRepository mappingRepository;

    // Spring이 관측 서비스, JSON 변환 도구, DB 매핑 조회기를 주입한다.
    public StyleCoreCandidateService(
            SessionObservationService observationService,
            JsonMapper mapper,
            StyleCoreDirectMappingRepository mappingRepository
    ) {
        this.observationService = observationService;
        this.mapper = mapper;
        this.mappingRepository = mappingRepository;
    }

    // DB의 Q02 매핑을 기존 후보 추출기가 사용하는 형태로 변환한다.
    // JSON 파일은 읽지 않으며, 후보 판별 규칙은 기존 추출기에 위임한다.
    private JsonNode loadDirectMapping() {
        String definitionId = "22222222-2222-4222-8222-222222222222";

        var rules = mappingRepository
                .findByDefinitionAndQuestion(definitionId, "Q02")
                .stream()
                .map(row -> Map.of(
                        "optionId", row.optionId(),
                        "candidateId", row.candidateId()
                ))
                .toList();

        if (rules.isEmpty()) {
            throw new IllegalStateException(
                    "DIRECT_STYLE_MAPPING_NOT_FOUND"
            );
        }

        return mapper.valueToTree(Map.of(
                "sourceDefinition", "SIMPLE-V1-DRAFT",
                "sourceDefinitionId", definitionId,
                "sourceQuestionId", "Q02",
                "sourceObservationType", "DIRECT_FASHION_LOOK_PREFERENCE",
                "rules", rules
        ));
    }

    // 회원의 실제 세션 관측을 DB의 Q02 직접 선호 매핑과 대조한다.
    // 직접 선택한 스타일 후보만 추출하며 최종 종합 RESULT는 확정하지 않는다.
    public Set<String> extractFromSession(
            String memberId,
            String sessionId
    ) {
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

        JsonNode mapping = loadDirectMapping();

        return StyleCoreCandidateExtractor.extract(
                observations,
                mapping
        );

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
        var rawObservations =
                observationService.collectRaw(
                        memberId,
                        sessionId
                );

        JsonNode observations = mapper.readTree(
                mapper.writeValueAsString(rawObservations)
        );

        JsonNode mapping = loadDirectMapping();

        return StyleCoreCandidateExtractor.extractWithEvidence(
                observations,
                mapping
        );
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

    // 실제 Simple 세션의 초기 후보들 사이에서 공통·차별 근거를 구분한다.
    // 실험용 속성 매핑 결과를 비교하며, 점수나 최종 RESULT는 확정하지 않는다.
    public SimpleCandidateAttributeMatcher.EvidenceComparison
    compareSimpleCandidateEvidenceFromSession(
            String memberId,
            String sessionId
    ) {
        var matches = matchSimpleAttributesFromSession(
                memberId,
                sessionId
        );

        return SimpleCandidateAttributeMatcher.compareEvidence(
                matches
        );
    }

    // 실제 Simple 세션에서 후보 간 속성 판별 신호를 평가한다.
    // 실험용 매핑에서 관측된 잠정 우세 여부만 판단하며
    // 최종 RESULT나 대표 스타일은 확정하지 않는다.
    public SimpleCandidateDiscrimination.AttributeSignalAssessment
    assessSimpleAttributeSignalsFromSession(
            String memberId,
            String sessionId
    ) {
        var comparison =
                compareSimpleCandidateEvidenceFromSession(
                        memberId,
                        sessionId
                );

        return SimpleCandidateDiscrimination.assessAttributeSignals(
                comparison
        );
    }

    // 실제 Simple 세션의 관측을 R01·R02 후보 속성 프로필과 대조한다.
    // 공유 근거와 양립 가능한 속성, 아직 해석하지 못한 관측을 구분한다.
    public SimpleCandidateDiscrimination.ProfileEvidenceReview
    reviewSimpleCandidateProfilesFromSession(
            String memberId,
            String sessionId
    ) {
        SimpleObservationProfile profile =
                buildSimpleObservationProfile(
                        memberId,
                        sessionId
                );

        try {
            JsonNode candidateProfiles = mapper.readTree(
                    Files.readString(Path.of(
                            "dev", "fixtures", "result",
                            "simple_style_core_candidate_profiles_v0.1.json"
                    ))
            );

            return SimpleCandidateDiscrimination.reviewCandidateProfiles(
                    profile,
                    candidateProfiles
            );

        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "CANDIDATE_PROFILES_LOAD_FAILED",
                    e
            );
        }
    }

    // 실제 Simple 세션에서 관측의 처리 상태를 분류한다.
    // 이미 해석한 속성과 아직 매핑 또는 시각 정보가 필요한 관측을 구별한다.
    public java.util.Map<String, Set<String>>
    classifySimpleEvidenceReadinessFromSession(
            String memberId,
            String sessionId
    ) {
        SimpleObservationProfile profile =
                buildSimpleObservationProfile(
                        memberId,
                        sessionId
                );

        var review = reviewSimpleCandidateProfilesFromSession(
                memberId,
                sessionId
        );

        return SimpleCandidateDiscrimination.classifyEvidenceReadiness(
                profile,
                review
        );
    }

    // Simple 세션의 판별에 필요한 중간 결과를 하나로 묶는다.
    // 아직 최종 RESULT를 확정하지 않으며, 판별 근거와 준비 상태를 보존한다.
    public record SimpleResultAnalysis(
            Set<String> initialCandidates,
            List<SimpleCandidateAttributeMatcher.CandidateMatches> attributeMatches,
            SimpleCandidateAttributeMatcher.EvidenceComparison evidenceComparison,
            SimpleCandidateDiscrimination.AttributeSignalAssessment signalAssessment,
            SimpleCandidateDiscrimination.ProfileEvidenceReview profileReview,
            Map<String, Set<String>> evidenceReadiness
    ) {
        public SimpleResultAnalysis {
            initialCandidates = Set.copyOf(initialCandidates);
            attributeMatches = List.copyOf(attributeMatches);
            evidenceReadiness = Map.copyOf(evidenceReadiness);
        }
    }

    // 실제 Simple 세션의 Raw 관측을 한 번만 수집해
    // 초기 후보부터 속성 관계 분석과 판별 신호 평가까지 수행한다.
    public SimpleResultAnalysis analyzeSimpleSession(
            String memberId,
            String sessionId
    ) {
        // 하나의 Raw 관측 스냅샷을 전체 분석에서 공유한다.
        var rawObservations = observationService.collectRaw(
                memberId,
                sessionId
        );

        SimpleObservationProfile profile =
                new SimpleObservationProfile(rawObservations);

        try {
            JsonNode observations = mapper.readTree(
                    mapper.writeValueAsString(rawObservations)
            );

            // Q02 직접 선호 매핑은 DB에서 조회한다.
            JsonNode directMapping = loadDirectMapping();

            // 실험용 속성 매핑은 아직 기존 설정을 사용한다.
            JsonNode attributeMapping = mapper.readTree(
                    Files.readString(Path.of(
                            "dev", "fixtures", "result",
                            "simple_style_core_attribute_mapping_v0.1.json"
                    ))
            );

            JsonNode candidateProfiles = mapper.readTree(
                    Files.readString(Path.of(
                            "dev", "fixtures", "result",
                            "simple_style_core_candidate_profiles_v0.1.json"
                    ))
            );

            // Q02의 명시적 선택만 초기 후보로 추출한다.
            Set<String> initialCandidates =
                    StyleCoreCandidateExtractor.extract(
                            observations,
                            directMapping
                    );

            // 실제 초기 후보에 속하는 매칭 결과만 사용한다.
            var attributeMatches =
                    SimpleCandidateAttributeMatcher.match(
                                    profile,
                                    attributeMapping
                            ).stream()
                            .filter(candidate ->
                                    initialCandidates.contains(
                                            candidate.candidateId()
                                    )
                            )
                            .toList();

            // 공통·차별 근거를 분리한다.
            var comparison =
                    SimpleCandidateAttributeMatcher.compareEvidence(
                            attributeMatches
                    );

            // 매핑상의 차이가 검증된 후보 우세로 오인되지 않도록 평가한다.
            var signalAssessment =
                    SimpleCandidateDiscrimination.assessAttributeSignals(
                            comparison
                    );

            // 후보 프로필에 정의된 공유·양립 관계를 분석한다.
            var profileReview =
                    SimpleCandidateDiscrimination.reviewCandidateProfiles(
                            profile,
                            candidateProfiles
                    );

            // 이미 활용한 관측과 추가 정의가 필요한 관측을 구분한다.
            var readiness =
                    SimpleCandidateDiscrimination.classifyEvidenceReadiness(
                            profile,
                            profileReview
                    );

            return new SimpleResultAnalysis(
                    initialCandidates,
                    attributeMatches,
                    comparison,
                    signalAssessment,
                    profileReview,
                    readiness
            );

        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "SIMPLE_RESULT_ANALYSIS_CONFIGURATION_FAILED",
                    e
            );
        }
    }

    // Simple RESULT의 선정 가능 상태를 표현한다.
    // 미검증 근거만 존재할 경우 RESULT 확정을 허용하지 않는다.
    public record SimpleResultDecision(
            String status,
            Set<String> eligibleCandidates,
            String representativeCandidate,
            String finalResult,
            Set<String> blockers
    ) {
        public SimpleResultDecision {
            eligibleCandidates = Set.copyOf(eligibleCandidates);
            blockers = Set.copyOf(blockers);
        }
    }

    // Simple 분석 결과에서 대표 후보와 최종 RESULT 선정 상태를 판단한다.
    // 직접 선호 후보가 하나라면 대표 후보로 지정하되,
    // 검증된 최종 판별 규칙이 없으므로 finalResult는 확정하지 않는다.
    public SimpleResultDecision decideSimpleResult(
            SimpleResultAnalysis analysis
    ) {
        if (analysis == null) {
            throw new IllegalArgumentException(
                    "INVALID_SIMPLE_RESULT_ANALYSIS"
            );
        }

        Set<String> candidates = analysis.initialCandidates();
        Set<String> blockers = new java.util.LinkedHashSet<>();

        if (candidates.isEmpty()) {
            return new SimpleResultDecision(
                    "NO_CANDIDATE",
                    candidates,
                    null,
                    null,
                    Set.of("NO_INITIAL_CANDIDATE")
            );
        }

        // Q02에서 하나의 스타일을 직접 선택한 경우다.
        // 대표 직접 선호로 표시하되, 종합 RESULT는 확정하지 않는다.
        if (candidates.size() == 1) {
            String selectedStyle = candidates.iterator().next();

            return new SimpleResultDecision(
                    "DIRECT_PREFERENCE_RESULT",
                    candidates,
                    selectedStyle,
                    null,
                    Set.of("COMPREHENSIVE_CLASSIFICATION_NOT_VERIFIED")
            );
        }

        // 복수 후보가 존재하면 아직 우세를 확정하지 않는다.
        // 실험용 속성 매칭의 차이를 검증된 판별 근거로 사용하지 않는다.
        var signal = analysis.signalAssessment();

        String leadCandidate = signal.provisionalLeadCandidate();

        if (leadCandidate != null
                && candidates.contains(leadCandidate)
                && "VERIFIED_LEAD".equals(signal.status())) {

            return new SimpleResultDecision(
                    "REPRESENTATIVE_SELECTED",
                    candidates,
                    leadCandidate,
                    null,
                    Set.of("FINAL_CLASSIFICATION_RULE_NOT_VERIFIED")
            );
        }

        blockers.add("MULTIPLE_INITIAL_CANDIDATES");
        blockers.add("NO_VERIFIED_CANDIDATE_DIFFERENTIATION");

        var visualPending = analysis.evidenceReadiness().getOrDefault(
                "VISUAL_DEFINITION_PENDING",
                Set.of()
        );

        if (!visualPending.isEmpty()) {
            blockers.add("VISUAL_EVIDENCE_NOT_READY");
        }

        var attributePending = analysis.evidenceReadiness().getOrDefault(
                "ATTRIBUTE_MAPPING_PENDING",
                Set.of()
        );

        if (!attributePending.isEmpty()) {
            blockers.add("ATTRIBUTE_MAPPING_INCOMPLETE");
        }

        blockers.add("FINAL_CLASSIFICATION_RULE_NOT_VERIFIED");

        return new SimpleResultDecision(
                "DIRECT_MULTI_PREFERENCE_RESULT",
                candidates,
                null,
                null,
                blockers
        );
    }

    // 검증된 Q08 후보 지지 근거를 이용해 복수 후보의 대표 스타일을 평가한다.
    // 한 후보에만 긍정 근거가 관측된 경우 1차 대표 후보를 선정한다.
    // 단, 다른 후보를 부정하거나 최종 종합 RESULT를 확정하지 않는다.
    public SimpleResultDecision decideWithQ08Evidence(
            SimpleResultAnalysis analysis,
            List<Q08ComparisonEvidenceService.CandidateSupport> supports
    ) {
        if (analysis == null || supports == null) {
            throw new IllegalArgumentException(
                    "INVALID_Q08_RESULT_INPUT"
            );
        }

        SimpleResultDecision baseline =
                decideSimpleResult(analysis);

        Set<String> candidates = analysis.initialCandidates();

        // 후보가 하나 이하라면 기존 직접 선호 판별 결과를 유지한다.
        if (candidates.size() <= 1) {
            return baseline;
        }

        Set<String> supportedCandidates =
                new java.util.LinkedHashSet<>();

        for (var support : supports) {
            if (support == null) {
                throw new IllegalArgumentException(
                        "INVALID_Q08_RESULT_INPUT"
                );
            }

            if (candidates.contains(support.candidateId())) {
                supportedCandidates.add(support.candidateId());
            }
        }

        // 검증된 Q08 지지 근거가 정확히 한 후보에만 존재한다.
        if (supportedCandidates.size() == 1) {
            String representative =
                    supportedCandidates.iterator().next();

            return new SimpleResultDecision(
                    "Q08_SUPPORTED_REPRESENTATIVE",
                    candidates,
                    representative,
                    null,
                    Set.of("COMPREHENSIVE_CLASSIFICATION_NOT_VERIFIED")
            );
        }

        // 둘 이상의 후보가 지지되거나 지지 근거가 없다면
        // 현재 정책으로는 대표 후보를 선정하지 않는다.
        // 복수 후보에 대한 검증된 Q08 지지 근거가 없거나
        // 여러 후보를 동시에 지지하는 경우다.
        // 대표 후보는 정하지 않지만 복수 직접 선호는 유지한다.
        Set<String> remainingBlockers =
                new java.util.LinkedHashSet<>(baseline.blockers());

        remainingBlockers.add("REPRESENTATIVE_SELECTION_PENDING");

        return new SimpleResultDecision(
                "DIRECT_MULTI_PREFERENCE_RESULT",
                candidates,
                null,
                null,
                remainingBlockers
        );
    }

    // 실제 Simple 세션을 분석하고 DB에 검증된 Q08 근거를 결합한다.
    // 검증 근거가 충분하지 않으면 기존 직접 선호 결과를 유지한다.
    public SimpleResultDecision decideSimpleResultFromSession(
            String memberId,
            String sessionId,
            Q08ComparisonEvidenceService q08EvidenceService
    ) {
        if (q08EvidenceService == null) {
            throw new IllegalArgumentException(
                    "INVALID_Q08_EVIDENCE_SERVICE"
            );
        }

        // 기존 통합 분석을 이용해 Q02 초기 후보와 속성 관계를 확인한다.
        SimpleResultAnalysis analysis = analyzeSimpleSession(
                memberId,
                sessionId
        );

        // 단일 후보 또는 후보 없음은 Q08 비교 없이 처리한다.
        if (analysis.initialCandidates().size() <= 1) {
            return decideSimpleResult(analysis);
        }

        // Q08 DB 데이터가 검증된 경우에만 후보 지지 근거를 가져온다.
        var supports = q08EvidenceService.collectCandidateSupport(
                memberId,
                sessionId,
                analysis.initialCandidates()
        );

        return decideWithQ08Evidence(
                analysis,
                supports
        );
    }

    // Simple 테스트 결과 화면에 전달할 데이터다.
    // 개발용 차단 사유와 검증되지 않은 판별 설명은 포함하지 않는다.
    public record SimpleResultView(
            String status,
            String representativeStyleId,
            List<String> preferredStyleIds,
            String title,
            String description,
            boolean comprehensiveResult
    ) {
        public SimpleResultView {
            preferredStyleIds = preferredStyleIds.stream()
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    // 내부 판별 결과를 사용자에게 전달할 RESULT 형태로 변환한다.
    // 현재는 직접 선호 기반 결과와 Q08 근거 기반 대표 후보를 구분한다.
    public SimpleResultView buildSimpleResultView(
            SimpleResultDecision decision
    ) {
        if (decision == null) {
            throw new IllegalArgumentException(
                    "INVALID_SIMPLE_RESULT_DECISION"
            );
        }

        String status = decision.status();

        if ("DIRECT_PREFERENCE_RESULT".equals(status)) {
            return new SimpleResultView(
                    status,
                    decision.representativeCandidate(),
                    List.copyOf(decision.eligibleCandidates()),
                    "직접 선택한 스타일",
                    "테스트에서 직접 선호한다고 선택한 스타일입니다.",
                    false
            );
        }

        if ("Q08_SUPPORTED_REPRESENTATIVE".equals(status)
                || "REPRESENTATIVE_SELECTED".equals(status)) {
            return new SimpleResultView(
                    status,
                    decision.representativeCandidate(),
                    List.copyOf(decision.eligibleCandidates()),
                    "현재 가장 유력한 스타일",
                    "직접 선택한 스타일과 검증된 추가 근거를 바탕으로 선정한 대표 후보입니다.",
                    false
            );
        }

        if ("DIRECT_MULTI_PREFERENCE_RESULT".equals(status)) {
            return new SimpleResultView(
                    status,
                    null,
                    List.copyOf(decision.eligibleCandidates()),
                    "여러 스타일에 끌리는 취향",
                    "선택한 스타일이 여러 개이며, 현재 근거만으로 하나의 대표 스타일을 결정하지 않았습니다.",
                    false
            );
        }

        return new SimpleResultView(
                status,
                null,
                List.copyOf(decision.eligibleCandidates()),
                "취향 분석 결과를 준비하고 있습니다",
                "현재 응답만으로는 대표 스타일을 확인하기 어렵습니다.",
                false
        );
    }

    // 실제 Simple 세션의 응답을 분석하여 사용자용 RESULT를 생성한다.
    // Q08의 검증된 DB 근거를 활용하고, 내부 판별 사유는 노출하지 않는다.
    public SimpleResultView buildSimpleResultViewFromSession(
            String memberId,
            String sessionId,
            Q08ComparisonEvidenceService q08EvidenceService
    ) {
        // 실제 세션의 관측에서 대표 후보 선정까지 수행한다.
        SimpleResultDecision decision =
                decideSimpleResultFromSession(
                        memberId,
                        sessionId,
                        q08EvidenceService
                );

        // 내부 판별 결과를 사용자에게 보여줄 데이터로 변환한다.
        return buildSimpleResultView(decision);
    }

}
