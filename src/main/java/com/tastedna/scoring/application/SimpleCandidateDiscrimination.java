package com.tastedna.scoring.application;

import tools.jackson.databind.JsonNode;
import com.tastedna.scoring.application.SimpleInitialCandidateService.Source;
import com.tastedna.scoring.application.SimpleInitialCandidateService.Observation;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

// Simple RESULT 초기 후보의 구별 가능성과 판별 보류 사유를 표현한다.
// 승인된 Simple B 충분성만 최종 경로에 사용하며 미승인 관계는 보류한다.
public record SimpleCandidateDiscrimination(
        Set<String> eligibleCandidates,
        String representativeCandidate,
        String finalResult,
        String reason
) {

    public static final String RULE_VERSION = "simple-B-v4-composite10B/S2-SHA256-v1/representative-SHA256-v1";

    public static final String INSUFFICIENT_EVIDENCE =
            "INSUFFICIENT_DISCRIMINATING_EVIDENCE";

    public record CandidateReview(String candidateId, List<Source> directPreference,
                                  List<Source> additionalSupport, Set<String> supportingQuestions,
                                  List<Observation> approvedConflicts, List<Observation> unresolved,
                                  List<String> missingRequirements) {}

    public record CandidatePoolReview(UUID sessionId, UUID definitionId, long revision,
                                      List<CandidateReview> candidates, List<String> validatedCandidateIds,
                                      List<String> missingRequirements) {}

    // 본인·제출·최신 답변 검증을 거친 초기 후보 응답만 평가한다. 질문 수는 출처 요약이지 점수가 아니다.
    public static CandidatePoolReview reviewInitialCandidates(SimpleInitialCandidateService.Response initial) {
        var reviews = new ArrayList<CandidateReview>();
        var validated = new TreeSet<String>();
        for (var candidate : initial.candidates()) {
            var positive = candidate.evidence().positive().stream().distinct()
                    .sorted(Comparator.comparing(Source::questionId).thenComparing(Source::optionId)
                            .thenComparingLong(Source::answerRevision).thenComparing(Source::observationType)
                            .thenComparing(Source::evidenceType).thenComparing(Source::optionLabel)).toList();
            var direct = positive.stream().filter(candidate.sources()::contains).toList();
            var additional = positive.stream().filter(s -> !candidate.sources().contains(s)).toList();
            var questions = new TreeSet<String>();
            positive.forEach(s -> questions.add(s.questionId()));
            var conflicts = candidate.evidence().avoidanceOrConflict().stream().distinct().toList();
            var unresolved = candidate.evidence().unresolved().stream().distinct().toList();
            var missing = new ArrayList<String>();
            if (positive.isEmpty()) missing.add("NO_APPROVED_POSITIVE_EVIDENCE");
            if (!conflicts.isEmpty()) missing.add("CONFLICT_RESOLUTION_RULE_REQUIRED");
            if (!unresolved.isEmpty()) missing.add("UNRESOLVED_OBSERVATION_RELATIONS");
            boolean sufficient = additional.stream().anyMatch(s -> sufficientVisual(initial.definitionId(), candidate.candidateId(), s))
                    || sufficientComposite(initial.definitionId(), candidate.candidateId(), positive);
            if (!sufficient) missing.add("CANDIDATE_SUFFICIENCY_RULE_REQUIRED");
            if (sufficient && conflicts.isEmpty()) validated.add(candidate.candidateId());
            reviews.add(new CandidateReview(candidate.candidateId(), direct, additional,
                    Collections.unmodifiableSet(questions), conflicts, unresolved, List.copyOf(missing)));
        }
        reviews.sort(Comparator.comparing(CandidateReview::candidateId));
        var missing = new ArrayList<String>();
        if (validated.isEmpty()) {
            missing.add("CANDIDATE_SUFFICIENCY_RULE_REQUIRED");
            if (reviews.stream().anyMatch(r -> !r.approvedConflicts().isEmpty())) missing.add("CONFLICT_RESOLUTION_RULE_REQUIRED");
        }
        return new CandidatePoolReview(initial.sessionId(), initial.definitionId(), initial.revision(),
                List.copyOf(reviews), List.copyOf(validated), List.copyOf(missing));
    }

    // 복합 조건은 실제 선호의 공존에 관한 Simple 해석이며 직접 혼합 착장이나 속성 주도성의 증명이 아니다.
    private record CompositeRule(List<String> entry, List<List<String>> alternatives) {
        boolean enters(Set<String> keys) { return keys.containsAll(entry); }
        boolean sufficient(Set<String> keys) {
            return enters(keys) && alternatives.stream().anyMatch(keys::containsAll);
        }
    }

    private static CompositeRule composite(String entry, String... alternatives) {
        return new CompositeRule(List.of(entry.split(" ")),
                Arrays.stream(alternatives).map(a -> List.of(a.split(" "))).toList());
    }

    private static Map<String, CompositeRule> compositeRules(UUID definition) {
        boolean mens = "33333333-3333-4333-8333-333333333333".equals(definition.toString());
        if (!mens && !"6acc799d-1348-4f34-b756-593e9f5bfd52".equals(definition.toString())) return Map.of();
        String[] vintage = mens ? new String[]{"Q05/D", "Q10/E"} : new String[]{"Q05/E", "Q06/F"};
        var rules = new LinkedHashMap<String, CompositeRule>();
        rules.put("R02", composite("Q02/R02", "Q04/P01 Q03/S05"));
        rules.put("R06", composite("Q02/R06", mens ? new String[]{"Q05/C"} : new String[]{"Q06/B", "Q10/H"}));
        rules.put("R08", composite("Q02/R08", vintage));
        rules.put("R10", composite("Q02/R10", "Q04/P06 Q03/S06"));
        rules.put("R11", composite("Q02/R11", mens ? "Q10/G" : "Q10/F"));
        rules.put("R12", composite("Q02/R12", mens ? "Q09/C01:B" : "Q10/P"));
        rules.put("R14", composite("Q02/R08", "Q04/P03"));
        rules.put("R18", composite("Q02/R01 Q02/R07", "Q04/P01"));
        rules.put("R19", composite("Q02/R08 Q02/R05", vintage));
        rules.put("R20", composite("Q02/R09 Q02/R11", "Q04/P06"));
        if (!mens) rules.put("R21", composite("Q01/O", "Q09/C04:B"));
        rules.put("R22", composite("Q03/S04", "Q08/C01:B"));
        rules.put("R23", composite("Q04/P03", "Q09/C02:A"));
        rules.put("R24", composite("Q02/R08 Q02/R11", vintage));
        rules.put("R25", composite("Q02/R03 Q02/R07", "Q04/P03"));
        return rules;
    }

    static Set<String> compositeObservationKeys(UUID definition) {
        var keys = new HashSet<String>();
        compositeRules(definition).values().forEach(r -> {
            keys.addAll(r.entry());
            r.alternatives().forEach(keys::addAll);
        });
        return keys;
    }

    static String compositeObservationType(UUID definition, String question) {
        return switch (question) {
            case "Q01" -> "DIRECT_COLOR_PREFERENCE";
            case "Q02" -> "DIRECT_FASHION_LOOK_PREFERENCE";
            case "Q03" -> "DIRECT_SILHOUETTE_PREFERENCE";
            case "Q04" -> "DIRECT_PATTERN_GRAPHIC_PREFERENCE";
            case "Q05" -> "DIRECT_OUTFIT_PREFERENCE";
            case "Q06" -> "DIRECT_CLOTHING_PREFERENCE";
            case "Q08" -> "DIRECT_COMPARISON_PREFERENCE";
            case "Q09" -> "33333333-3333-4333-8333-333333333333".equals(definition.toString())
                    ? "DIRECT_COMBINATION_PREFERENCE" : "DIRECT_COMPARISON_PREFERENCE";
            case "Q10" -> "DIRECT_FINAL_OUTFIT_PREFERENCE";
            default -> "";
        };
    }

    private static Set<String> compositeKeys(UUID definition, List<Source> sources) {
        var keys = new HashSet<String>();
        for (var source : sources) {
            boolean ranked = "6acc799d-1348-4f34-b756-593e9f5bfd52".equals(definition.toString())
                    && "Q09".equals(source.questionId()) && "C02:A".equals(source.optionId());
            boolean valid = ranked
                    ? "RANKED_COMPARISON_PREFERENCE".equals(source.observationType())
                        && "EXPLICIT_RANKED_SELECTION".equals(source.evidenceType()) && Integer.valueOf(1).equals(source.rank())
                    : compositeObservationType(definition, source.questionId()).equals(source.observationType())
                        && "EXPLICIT_SELECTION".equals(source.evidenceType()) && source.rank() == null;
            if (valid) keys.add(source.questionId() + "/" + source.optionId());
        }
        return keys;
    }

    static Map<String, List<Source>> compositeSupport(UUID definition, List<Source> sources) {
        var keys = compositeKeys(definition, sources);
        var support = new LinkedHashMap<String, List<Source>>();
        compositeRules(definition).forEach((id, rule) -> {
            if (!rule.enters(keys)) return;
            var relevant = new HashSet<>(rule.entry());
            rule.alternatives().forEach(relevant::addAll);
            support.put(id, sources.stream().filter(s -> relevant.contains(s.questionId() + "/" + s.optionId())
                    && keys.contains(s.questionId() + "/" + s.optionId())).distinct().toList());
        });
        return support;
    }

    static boolean sufficientComposite(UUID definition, String candidate, List<Source> sources) {
        var rule = compositeRules(definition).get(candidate);
        return rule != null && rule.sufficient(compositeKeys(definition, sources));
    }

    // 출처·이미지·최신 답변 검사를 통과한 긍정 근거에만 승인된 B 정책을 적용한다.
    private static boolean sufficientVisual(UUID definition, String candidate, Source source) {
        if ("6acc799d-1348-4f34-b756-593e9f5bfd52".equals(definition.toString()) && "R15".equals(candidate))
            return "Q08".equals(source.questionId()) && "C05:D".equals(source.optionId())
                    && "RANKED_COMPARISON_PREFERENCE".equals(source.observationType())
                    && "EXPLICIT_RANKED_SELECTION".equals(source.evidenceType()) && Integer.valueOf(1).equals(source.rank());
        if (!"EXPLICIT_SELECTION".equals(source.evidenceType())) return false;
        String key = candidate + "/" + source.questionId() + "/" + source.optionId() + "/" + source.observationType();
        return switch (definition.toString()) {
            case "33333333-3333-4333-8333-333333333333" -> Set.of(
                    "R01/Q09/C03:A/DIRECT_COMBINATION_PREFERENCE",
                    "R07/Q09/C03:B/DIRECT_COMBINATION_PREFERENCE",
                    "R03/Q10/J/DIRECT_FINAL_OUTFIT_PREFERENCE",
                    "R05/Q10/A/DIRECT_FINAL_OUTFIT_PREFERENCE",
                    "R13/Q10/D/DIRECT_FINAL_OUTFIT_PREFERENCE",
                    "R16/Q05/B/DIRECT_OUTFIT_PREFERENCE",
                    "R17/Q05/C/DIRECT_OUTFIT_PREFERENCE").contains(key);
            case "6acc799d-1348-4f34-b756-593e9f5bfd52" -> Set.of(
                    "R04/Q10/I/DIRECT_FINAL_OUTFIT_PREFERENCE",
                    "R05/Q10/A/DIRECT_FINAL_OUTFIT_PREFERENCE",
                    "R09/Q05/C/DIRECT_OUTFIT_PREFERENCE",
                    "R13/Q05/B/DIRECT_OUTFIT_PREFERENCE",
                    "R13/Q10/G/DIRECT_FINAL_OUTFIT_PREFERENCE",
                    "R17/Q06/B/DIRECT_CLOTHING_PREFERENCE",
                    "R17/Q10/H/DIRECT_FINAL_OUTFIT_PREFERENCE").contains(key);
            default -> false;
        };
    }

    // 충분성이 성립하고 승인된 우열·충돌이 없는 동등 후보만 S2 비복원 추첨에 전달한다.
    public static List<String> reduceEquivalentValidatedCandidates(UUID sessionId, long revision,
            Collection<String> candidates, String ruleVersion) {
        if (sessionId == null || revision < 0 || ruleVersion == null || ruleVersion.isBlank()
                || candidates == null || candidates.stream().anyMatch(id -> id == null || !id.matches("R(0[1-9]|1[0-9]|2[0-5])")))
            throw new IllegalArgumentException("INVALID_REDUCTION_INPUT");
        var ids = new ArrayList<>(candidates.stream().distinct().sorted().toList());
        if (ids.size() <= 5) return List.copyOf(ids);
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String field : List.of("TasteDNA-S2-SHA256-v1", sessionId.toString(), Long.toString(revision),
                    ruleVersion, Integer.toString(ids.size()))) updateSelectionDigest(digest, field);
            ids.forEach(id -> updateSelectionDigest(digest, id));
            var random = new Random(ByteBuffer.wrap(digest.digest()).getLong());
            // SHA-256 상위 64비트 → Java Random → Fisher-Yates 순서를 S2-v1 계약으로 고정한다.
            for (int size = ids.size(); size > 1; size--) Collections.swap(ids, size - 1, random.nextInt(size));
            return ids.subList(0, 5).stream().sorted().toList();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }

    // 충분성 검증을 마친 후보만 호출자가 전달한다. 충분성 평가·축소 이후에만 호출한다.
    // UTF-8 길이 접두 인코딩과 SHA-256-v1을 고정하여 재조회·입력 순서에 따른 재추첨을 막는다.
    public static Optional<String> selectValidatedRepresentative(
            UUID sessionId, long submissionRevision, Collection<String> validatedCandidateIds, String ruleVersion) {
        if (sessionId == null || submissionRevision < 0 || ruleVersion == null || ruleVersion.isBlank()
                || validatedCandidateIds == null || validatedCandidateIds.stream().anyMatch(
                id -> id == null || !id.matches("R(0[1-9]|1[0-9]|2[0-5])")))
            throw new IllegalArgumentException("INVALID_REPRESENTATIVE_SELECTION_INPUT");
        var ids = validatedCandidateIds.stream().distinct().sorted().toList();
        if (ids.isEmpty()) return Optional.empty();
        if (ids.size() > 5) throw new IllegalArgumentException("CANDIDATE_LIMIT_POLICY_REQUIRED");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String field : List.of("TasteDNA-representative-SHA256-v1", sessionId.toString(),
                    Long.toString(submissionRevision), ruleVersion, Integer.toString(ids.size())))
                updateSelectionDigest(digest, field);
            for (String id : ids) updateSelectionDigest(digest, id);
            int index = new BigInteger(1, digest.digest()).mod(BigInteger.valueOf(ids.size())).intValue();
            return Optional.of(ids.get(index));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }

    private static void updateSelectionDigest(MessageDigest digest, String field) {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    // 후보 간 판별 규칙이 검증되지 않은 경우 확정을 보류한다.
    // 후보가 하나만 남아 있더라도 자동으로 최종 RESULT로 확정하지 않는다.
    public static SimpleCandidateDiscrimination unresolved(
            Set<String> candidates
    ) {
        return new SimpleCandidateDiscrimination(
                Set.copyOf(candidates),
                null,
                null,
                INSUFFICIENT_EVIDENCE
        );
    }

    // 후보별 실제 관측 근거를 판별 설정과 대조한다.
    // 현재 R01·R02 판별 설정은 미검증 상태이므로 최종 판별은 보류한다.
    public static SimpleCandidateDiscrimination assess(
            List<ResultCandidateEvidence> evidence,
            JsonNode configuration
    ) {
        if (evidence == null
                || configuration == null
                || !configuration.isObject()) {
            throw new IllegalArgumentException(
                    "INVALID_DISCRIMINATION_INPUT"
            );
        }

        JsonNode status = configuration.path("status");
        JsonNode candidatePair = configuration.path("candidatePair");
        JsonNode verifiedQ02 = configuration
                .path("verifiedEvidence")
                .path("Q02");

        if (!status.isString()
                || !candidatePair.isArray()
                || candidatePair.size() != 2
                || !verifiedQ02.isObject()) {
            throw new IllegalArgumentException(
                    "INVALID_DISCRIMINATION_CONFIGURATION"
            );
        }

        // 현재 검증 대상으로 등록된 두 후보만 평가한다.
        Set<String> allowedCandidates = new LinkedHashSet<>();

        for (JsonNode candidate : candidatePair) {
            if (!candidate.isString()
                    || candidate.stringValue().isBlank()
                    || !allowedCandidates.add(candidate.stringValue())) {
                throw new IllegalArgumentException(
                        "INVALID_DISCRIMINATION_CONFIGURATION"
                );
            }
        }

        Set<String> observedCandidates = new LinkedHashSet<>();

        for (ResultCandidateEvidence candidateEvidence : evidence) {
            if (candidateEvidence == null
                    || !allowedCandidates.contains(
                    candidateEvidence.candidateId())
                    || candidateEvidence.sources().isEmpty()
                    || !observedCandidates.add(
                    candidateEvidence.candidateId())) {
                throw new IllegalArgumentException(
                        "INVALID_DISCRIMINATION_EVIDENCE"
                );
            }

            JsonNode verified = verifiedQ02.path(
                    candidateEvidence.candidateId()
            );

            if (!verified.isString()
                    || !"EXPLICIT_POSITIVE".equals(
                    verified.stringValue())) {
                throw new IllegalArgumentException(
                        "UNVERIFIED_DISCRIMINATION_EVIDENCE"
                );
            }

            // 후보별 근거가 실제 Q02 명시적 선택인지 확인한다.
            for (ResultCandidateEvidence.EvidenceSource source
                    : candidateEvidence.sources()) {

                if (source == null
                        || !"Q02".equals(source.questionId())
                        || !candidateEvidence.candidateId()
                        .equals(source.optionId())
                        || !"DIRECT_FASHION_LOOK_PREFERENCE"
                        .equals(source.observationType())
                        || !"EXPLICIT_SELECTION"
                        .equals(source.evidenceType())
                        || source.answerRevision() < 1) {
                    throw new IllegalArgumentException(
                            "INVALID_DISCRIMINATION_EVIDENCE"
                    );
                }
            }
        }

        // 승인되지 않은 판별 규칙으로 후보 순위나 RESULT를 생성하지 않는다.
        if (!"DISCRIMINATION_NOT_VERIFIED".equals(
                status.stringValue())) {
            throw new IllegalStateException(
                    "DISCRIMINATION_RULE_NOT_SUPPORTED"
            );
        }

        return unresolved(observedCandidates);
    }

    public SimpleCandidateDiscrimination {
        eligibleCandidates = Set.copyOf(eligibleCandidates);
    }

    // 실험용 속성 근거에서 관측된 후보 간 차이를 평가한 결과다.
    // provisionalLeadCandidate는 최종 RESULT가 아닌 잠정 검토 대상이다.
    public record AttributeSignalAssessment(
            String status,
            String provisionalLeadCandidate,
            SimpleCandidateAttributeMatcher.EvidenceComparison evidence
    ) {
    }

    // 실험용 속성 매핑에서 관측된 후보 간 차이를 분석한다.
    // 매핑상의 차이와 검증된 스타일 판별 근거를 구분한다.
    public static AttributeSignalAssessment assessAttributeSignals(
            SimpleCandidateAttributeMatcher.EvidenceComparison comparison
    ) {
        if (comparison == null) {
            throw new IllegalArgumentException(
                    "INVALID_EVIDENCE_COMPARISON"
            );
        }

        Map<String, Set<String>> distinctive =
                comparison.distinctiveSelections();

        int candidateCount = distinctive.size();
        int candidatesWithDistinctiveEvidence = 0;

        for (var entry : distinctive.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                candidatesWithDistinctiveEvidence++;
            }
        }

        // 두 후보 미만이거나 매핑상 차이가 없으면
        // 비교 가능한 구별 신호가 없는 상태다.
        if (candidateCount < 2
                || candidatesWithDistinctiveEvidence == 0) {

            return new AttributeSignalAssessment(
                    "NO_DIFFERENTIATION",
                    null,
                    comparison
            );
        }

        // 한 후보에만 속성 매칭이 있어도 현재 매핑은 실험용이다.
        // 실제로 검증된 차별 관계가 아니므로 우세 후보를 지정하지 않는다.
        if (candidatesWithDistinctiveEvidence == 1) {

            return new AttributeSignalAssessment(
                    "UNVERIFIED_DIFFERENCE",
                    null,
                    comparison
            );
        }

        // 여러 후보에 각기 다른 매핑 근거가 존재한다.
        // 어느 근거도 검증된 차별 신호라고 단정하지 않는다.
        return new AttributeSignalAssessment(
                "COMPETING_SIGNALS",
                null,
                comparison
        );
    }

    // 실제 선택과 후보 프로필의 속성 관계를 연결한 결과다.
    // SHARED_SUPPORT와 COMPATIBLE은 최종 판별 근거가 아니다.
    public record ProfileEvidenceReview(
            Map<String, Set<String>> sharedSupport,
            Map<String, Set<String>> compatible,
            Map<String, Set<String>> unmapped
    ) {
        public ProfileEvidenceReview {
            sharedSupport = Map.copyOf(sharedSupport);
            compatible = Map.copyOf(compatible);
            unmapped = Map.copyOf(unmapped);
        }
    }

    // 사용자가 실제 선택한 속성을 후보별 프로필과 대조한다.
    // 현재는 관계를 분류하며 점수나 후보 우열을 계산하지 않는다.
    public static ProfileEvidenceReview reviewCandidateProfiles(
            SimpleObservationProfile profile,
            JsonNode candidateProfiles
    ) {
        if (profile == null
                || candidateProfiles == null
                || !candidateProfiles.isObject()
                || !"EXPERIMENTAL".equals(
                candidateProfiles.path("status").stringValue())
                || !candidateProfiles.path("candidateProfiles").isArray()) {
            throw new IllegalArgumentException(
                    "INVALID_CANDIDATE_PROFILES"
            );
        }

        Set<String> selections = new LinkedHashSet<>();

        // 명시적으로 선택한 실제 관측만 사용한다.
        for (var observations : profile.observationsByType().values()) {
            for (var observation : observations) {
                if ("EXPLICIT_SELECTION".equals(
                        observation.evidence())) {
                    selections.add(
                            observation.questionId()
                                    + ":"
                                    + observation.optionId()
                    );
                }
            }
        }

        Map<String, Set<String>> shared = new LinkedHashMap<>();
        Map<String, Set<String>> compatible = new LinkedHashMap<>();
        Map<String, Set<String>> unmapped = new LinkedHashMap<>();

        for (JsonNode candidate :
                candidateProfiles.path("candidateProfiles")) {

            String candidateId =
                    candidate.path("candidateId").stringValue();

            if (candidateId == null
                    || candidateId.isBlank()
                    || shared.containsKey(candidateId)
                    || !candidate.path("attributeRelations").isArray()) {
                throw new IllegalArgumentException(
                        "INVALID_CANDIDATE_PROFILES"
                );
            }

            Set<String> sharedSelections = new LinkedHashSet<>();
            Set<String> compatibleSelections = new LinkedHashSet<>();
            Set<String> mappedSelections = new LinkedHashSet<>();

            for (JsonNode relation :
                    candidate.path("attributeRelations")) {

                String questionId =
                        relation.path("questionId").stringValue();
                String optionId =
                        relation.path("optionId").stringValue();
                String relationType =
                        relation.path("relation").stringValue();

                if (questionId == null || questionId.isBlank()
                        || optionId == null || optionId.isBlank()) {
                    throw new IllegalArgumentException(
                            "INVALID_CANDIDATE_PROFILES"
                    );
                }

                String selection = questionId + ":" + optionId;

                if (!mappedSelections.add(selection)) {
                    throw new IllegalArgumentException(
                            "DUPLICATE_ATTRIBUTE_RELATION"
                    );
                }

                if ("SHARED_SUPPORT".equals(relationType)) {
                    if (selections.contains(selection)) {
                        sharedSelections.add(selection);
                    }
                } else if ("COMPATIBLE".equals(relationType)) {
                    if (selections.contains(selection)) {
                        compatibleSelections.add(selection);
                    }
                } else {
                    throw new IllegalArgumentException(
                            "UNSUPPORTED_ATTRIBUTE_RELATION"
                    );
                }
            }

            Set<String> missing = new LinkedHashSet<>(selections);
            missing.removeAll(mappedSelections);

            shared.put(candidateId, Set.copyOf(sharedSelections));
            compatible.put(
                    candidateId,
                    Set.copyOf(compatibleSelections)
            );
            unmapped.put(candidateId, Set.copyOf(missing));
        }

        return new ProfileEvidenceReview(
                shared,
                compatible,
                unmapped
        );
    }

    // Simple 관측을 현재 판별 파이프라인의 처리 상태에 따라 분류한다.
    // 사용하지 않은 근거를 부정적 취향으로 해석하지 않는다.
    public static Map<String, Set<String>> classifyEvidenceReadiness(
            SimpleObservationProfile profile,
            ProfileEvidenceReview review
    ) {
        if (profile == null || review == null) {
            throw new IllegalArgumentException(
                    "INVALID_EVIDENCE_READINESS_INPUT"
            );
        }

        Set<String> reviewedSelections = new LinkedHashSet<>();

        for (var selections : review.sharedSupport().values()) {
            reviewedSelections.addAll(selections);
        }

        for (var selections : review.compatible().values()) {
            reviewedSelections.addAll(selections);
        }

        Map<String, Set<String>> result = new LinkedHashMap<>();

        result.put(
                "INITIAL_CANDIDATE_EVIDENCE",
                new LinkedHashSet<>()
        );
        result.put(
                "ATTRIBUTE_REVIEWED",
                new LinkedHashSet<>()
        );
        result.put(
                "ATTRIBUTE_MAPPING_PENDING",
                new LinkedHashSet<>()
        );
        result.put(
                "VISUAL_DEFINITION_PENDING",
                new LinkedHashSet<>()
        );
        // 순위형 선택과 그룹 내 비선호는 일반 선택과 구분한다.
        result.put(
                "RANKED_PREFERENCE_EVIDENCE",
                new LinkedHashSet<>()
        );
        result.put(
                "RANKED_NON_PREFERENCE_EVIDENCE",
                new LinkedHashSet<>()
        );
        result.put(
                "OTHER_OBSERVATION",
                new LinkedHashSet<>()
        );

        for (var observations : profile.observationsByType().values()) {
            for (var observation : observations) {


                String evidence = observation.evidence();
                String questionId = observation.questionId();
                String selection = questionId + ":" + observation.optionId();

                // 순위형 근거는 그룹 내 선택·비선호 사실로만 보존한다.
                // 별도 승인 없이 후보의 지지·충돌이나 최종 점수로 변환하지 않는다.
                if ("EXPLICIT_RANKED_SELECTION".equals(evidence)) {
                    result.get("RANKED_PREFERENCE_EVIDENCE")
                            .add(selection);
                    continue;
                }

                if ("UNSELECTED_IN_RANKED_GROUP".equals(evidence)) {
                    result.get("RANKED_NON_PREFERENCE_EVIDENCE")
                            .add(selection);
                    continue;
                }

                if (!"EXPLICIT_SELECTION".equals(evidence)) {
                    continue;
                }


                if ("Q02".equals(questionId)) {

                    result.get(
                            "INITIAL_CANDIDATE_EVIDENCE"
                    ).add(selection);

                } else if (reviewedSelections.contains(selection)) {

                    result.get(
                            "ATTRIBUTE_REVIEWED"
                    ).add(selection);

                } else if (Set.of(
                        "Q01", "Q03", "Q04"
                ).contains(questionId)) {

                    result.get(
                            "ATTRIBUTE_MAPPING_PENDING"
                    ).add(selection);

                } else if (Set.of(
                        "Q05", "Q06", "Q07",
                        "Q08", "Q09", "Q10"
                ).contains(questionId)) {

                    result.get(
                            "VISUAL_DEFINITION_PENDING"
                    ).add(selection);

                } else {

                    result.get(
                            "OTHER_OBSERVATION"
                    ).add(selection);
                }
            }
        }

        Map<String, Set<String>> immutable =
                new LinkedHashMap<>();

        result.forEach((status, selections) ->
                immutable.put(
                        status,
                        Set.copyOf(selections)
                )
        );

        return Map.copyOf(immutable);
    }

}
