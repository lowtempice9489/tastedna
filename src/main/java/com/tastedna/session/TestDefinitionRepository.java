
package com.tastedna.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestDefinitionRepository
        extends JpaRepository<TestDefinition, String> {

    Optional<TestDefinition> findByTestModeAndActiveTrue(
            String testMode
    );
}
