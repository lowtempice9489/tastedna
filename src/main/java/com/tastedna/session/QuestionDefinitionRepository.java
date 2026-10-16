
package com.tastedna.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionDefinitionRepository
        extends JpaRepository<QuestionDefinition, QuestionDefinitionId> {

    List<QuestionDefinition> findByIdDefinitionIdOrderByDisplayOrderAsc(
            String definitionId
    );
}
