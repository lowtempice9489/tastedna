package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.OptionScoringRule;
import com.tastedna.scoring.domain.OptionScoringRuleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OptionScoringRuleRepository
        extends JpaRepository<OptionScoringRule, OptionScoringRuleId> {

    List<OptionScoringRule> findByIdDefinitionIdAndIdQuestionId(
            String definitionId,
            String questionId
    );
}
