package com.tastedna.definition.persistence;

import com.tastedna.definition.domain.TestDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestDefinitionRepository
        extends JpaRepository<TestDefinition, String> {

    Optional<TestDefinition> findByTestModeAndActiveTrue(
            String testMode
    );
}
