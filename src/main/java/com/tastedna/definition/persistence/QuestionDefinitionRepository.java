package com.tastedna.definition.persistence;

import com.tastedna.definition.domain.QuestionDefinition;
import com.tastedna.definition.domain.QuestionDefinitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionDefinitionRepository
        extends JpaRepository<QuestionDefinition, QuestionDefinitionId> {

    List<QuestionDefinition> findByIdDefinitionIdOrderByDisplayOrderAsc(
            String definitionId
    );

    Optional<QuestionDefinition> findByIdDefinitionIdAndIdQuestionId(
            String definitionId,
            String questionId
    );

}
