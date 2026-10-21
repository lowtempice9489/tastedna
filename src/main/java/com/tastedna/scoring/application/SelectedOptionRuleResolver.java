package com.tastedna.scoring.application;

import com.tastedna.scoring.domain.OptionScoringRule;
import com.tastedna.scoring.persistence.OptionScoringRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SelectedOptionRuleResolver {

    private final OptionScoringRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public Map<String, OptionScoringRule> resolve(
            String definitionId,
            String questionId,
            List<String> selectedOptionIds
    ) {
        if (selectedOptionIds == null
                || selectedOptionIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "INVALID_SELECTED_OPTIONS"
            );
        }

        List<OptionScoringRule> rules =
                ruleRepository.findByIdDefinitionIdAndIdQuestionId(
                        definitionId,
                        questionId
                );

        Map<String, OptionScoringRule> available =
                new LinkedHashMap<>();

        for (OptionScoringRule rule : rules) {
            available.put(
                    rule.getId().getOptionId(),
                    rule
            );
        }

        Map<String, OptionScoringRule> resolved =
                new LinkedHashMap<>();

        for (String optionId : selectedOptionIds) {

            OptionScoringRule rule = available.get(optionId);

            if (rule == null) {
                throw new IllegalStateException(
                        "SCORING_RULE_NOT_FOUND"
                );
            }

            if (resolved.putIfAbsent(optionId, rule) != null) {
                throw new IllegalArgumentException(
                        "DUPLICATE_SELECTED_OPTION"
                );
            }
        }

        return java.util.Collections.unmodifiableMap(resolved);
    }
}
