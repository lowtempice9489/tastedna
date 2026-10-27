package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.ComparisonDefinition;
import com.tastedna.scoring.domain.ComparisonDefinitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Q08 비교 정의를 MySQL에서 조회한다.
// JSON fixture 대신 DB의 비교 정의를 사용하는 신규 기능의 진입점이다.
public interface ComparisonDefinitionRepository
        extends JpaRepository<ComparisonDefinition, ComparisonDefinitionId> {

    // 특정 테스트 정의의 Q08 비교 항목 전체를 조회한다.
    List<ComparisonDefinition>
    findByIdDefinitionIdAndIdQuestionIdOrderByIdComparisonIdAsc(
            String definitionId,
            String questionId
    );

    // 특정 비교 항목 하나를 조회한다.
    Optional<ComparisonDefinition>
    findByIdDefinitionIdAndIdQuestionIdAndIdComparisonId(
            String definitionId,
            String questionId,
            String comparisonId
    );
}
