package com.tastedna.scoring.application;

import com.tastedna.scoring.domain.ComparisonDefinition;
import com.tastedna.scoring.domain.ComparisonOptionAttribute;
import com.tastedna.scoring.persistence.ComparisonCandidateEvidenceRepository;
import com.tastedna.scoring.persistence.ComparisonDefinitionRepository;
import com.tastedna.scoring.persistence.ComparisonOptionAttributeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Q08의 실제 선택을 DB의 검증된 비교 속성과 연결한다.
// A/B 자체를 스타일로 해석하거나 RESULT를 확정하지 않는다.
@Service
public class Q08ComparisonEvidenceService {

    private final SessionObservationService observationService;
    private final ComparisonDefinitionRepository definitionRepository;
    private final ComparisonOptionAttributeRepository optionRepository;
    private final ComparisonCandidateEvidenceRepository  candidateEvidenceRepository;

    public Q08ComparisonEvidenceService(
            SessionObservationService observationService,
            ComparisonDefinitionRepository definitionRepository,
            ComparisonOptionAttributeRepository optionRepository,
            ComparisonCandidateEvidenceRepository candidateEvidenceRepository
    ) {
        this.observationService = observationService;
        this.definitionRepository = definitionRepository;
        this.optionRepository = optionRepository;
        this.candidateEvidenceRepository = candidateEvidenceRepository;
    }

    // 검증된 한 쌍의 비교에서 실제 선택된 속성을 보존한다.
    public record VerifiedComparisonEvidence(
            String definitionId,
            String comparisonId,
            String selectedOptionId,
            String attributeKey,
            String selectedAttributeValue,
            String alternativeAttributeValue,
            long answerRevision
    ) {
    }

    // 세션의 Q08 관측을 조회하고 검증된 비교 근거만 반환한다.
    public List<VerifiedComparisonEvidence> collectVerified(
            String memberId,
            String sessionId
    ) {
        var raw = observationService.collectRaw(memberId, sessionId);

        var observations = raw.getOrDefault(
                "DIRECT_COMPARISON_PREFERENCE",
                List.of()
        );

        List<VerifiedComparisonEvidence> result = new ArrayList<>();

        for (var observation : observations) {

            if (!"Q08".equals(observation.questionId())
                    || !"EXPLICIT_SELECTION".equals(observation.evidence())) {
                continue;
            }

            // 실제 Raw 관측 형식: C01:A
            String selected = observation.optionId();
            if (selected == null) {
                continue;
            }

            String[] parts = selected.split(":", -1);

            if (parts.length != 2
                    || parts[0].isBlank()
                    || !("A".equals(parts[1]) || "B".equals(parts[1]))) {
                continue;
            }

            String comparisonId = parts[0];
            String selectedOptionId = parts[1];
            String definitionId = observation.definitionId();

            var definition = definitionRepository
                    .findByIdDefinitionIdAndIdQuestionIdAndIdComparisonId(
                            definitionId,
                            "Q08",
                            comparisonId
                    );

            if (definition.isEmpty() || !definition.get().isVerified()) {
                continue;
            }

            ComparisonDefinition comparison = definition.get();

            if (comparison.getComparisonFocus() == null
                    || comparison.getComparisonFocus().isBlank()) {
                continue;
            }

            var options = optionRepository
                    .findByIdDefinitionIdAndIdQuestionIdAndIdComparisonId(
                            definitionId,
                            "Q08",
                            comparisonId
                    );

            // A/B가 정확히 한 개씩 있어야 의미 있는 비교다.
            if (options.size() != 2) {
                continue;
            }

            ComparisonOptionAttribute a = null;
            ComparisonOptionAttribute b = null;

            for (var option : options) {
                if ("A".equals(option.getId().getOptionId())) {
                    a = option;
                } else if ("B".equals(option.getId().getOptionId())) {
                    b = option;
                }
            }

            if (a == null || b == null
                    || !a.hasAttributeDefinition()
                    || !b.hasAttributeDefinition()) {
                continue;
            }

            // 동일 속성에서 서로 다른 값을 비교해야 한다.
            if (!a.getAttributeKey().equals(b.getAttributeKey())
                    || !a.getAttributeKey().equals(
                    comparison.getComparisonFocus())
                    || a.getAttributeValue().equals(b.getAttributeValue())) {
                continue;
            }

            ComparisonOptionAttribute chosen =
                    "A".equals(selectedOptionId) ? a : b;

            ComparisonOptionAttribute alternative =
                    "A".equals(selectedOptionId) ? b : a;

            result.add(new VerifiedComparisonEvidence(
                    definitionId,
                    comparisonId,
                    selectedOptionId,
                    chosen.getAttributeKey(),
                    chosen.getAttributeValue(),
                    alternative.getAttributeValue(),
                    observation.answerRevision()
            ));
        }

        return List.copyOf(result);
    }

    // 실제 Q08 선택에서 확인된 후보 지지 근거를 표현한다.
    // 지지 근거는 대표 후보 확정과 구분한다.
    public record CandidateSupport(
            String candidateId,
            String comparisonId,
            String attributeKey,
            String selectedAttributeValue,
            long answerRevision
    ) {
    }

    // 검증된 Q08 속성을 실제 초기 RESULT 후보와 연결한다.
    // SUPPORT + VERIFIED 관계만 인정하며, 후보 우열은 결정하지 않는다.
    public List<CandidateSupport> collectCandidateSupport(
            String memberId,
            String sessionId,
            Set<String> initialCandidates
    ) {
        if (initialCandidates == null) {
            throw new IllegalArgumentException(
                    "INVALID_INITIAL_CANDIDATES"
            );
        }

        if (initialCandidates.isEmpty()) {
            return List.of();
        }

        var verifiedComparisons = collectVerified(
                memberId,
                sessionId
        );

        List<CandidateSupport> result = new ArrayList<>();

        for (var comparison : verifiedComparisons) {

            var candidateMappings = candidateEvidenceRepository
                    .findByIdDefinitionIdAndIdQuestionIdAndIdComparisonIdAndIdAttributeKeyAndIdAttributeValue(
                            comparison.definitionId(),
                            "Q08",
                            comparison.comparisonId(),
                            comparison.attributeKey(),
                            comparison.selectedAttributeValue()
                    );

            for (var mapping : candidateMappings) {

                if (!mapping.isVerifiedSupport()) {
                    continue;
                }

                String candidateId =
                        mapping.getId().getCandidateId();

                if (!initialCandidates.contains(candidateId)) {
                    continue;
                }

                result.add(new CandidateSupport(
                        candidateId,
                        comparison.comparisonId(),
                        comparison.attributeKey(),
                        comparison.selectedAttributeValue(),
                        comparison.answerRevision()
                ));
            }
        }

        return List.copyOf(result);
    }

}
