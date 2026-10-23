package com.tastedna.definition.persistence;

import com.tastedna.definition.domain.TestDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestDefinitionRepository
        extends JpaRepository<TestDefinition, String> {

    // 요청한 테스트 모드에서 활성화된 정의를 조회한다.
    // 새 세션 생성 시 사용하며, 활성 정의가 없으면 서비스가 세션 생성을 거부한다.
    Optional<TestDefinition> findByTestModeAndActiveTrue(
            String testMode
    );
}
