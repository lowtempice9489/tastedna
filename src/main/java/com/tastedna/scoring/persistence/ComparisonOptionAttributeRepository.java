package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.ComparisonOptionAttribute;
import com.tastedna.scoring.domain.ComparisonOptionAttributeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Q08 비교 선택지의 이미지·속성 정보를 DB에서 조회한다.
public interface ComparisonOptionAttributeRepository
        extends JpaRepository<
        ComparisonOptionAttribute,
        ComparisonOptionAttributeId
        > {

    // 하나의 비교 항목에 속한 A/B 선택지 정보를 조회한다.
    List<ComparisonOptionAttribute>
    findByIdDefinitionIdAndIdQuestionIdAndIdComparisonId(
            String definitionId,
            String questionId,
            String comparisonId
    );

    // 실제 사용자가 선택한 하나의 옵션을 조회한다.
    Optional<ComparisonOptionAttribute>
    findByIdDefinitionIdAndIdQuestionIdAndIdComparisonIdAndIdOptionId(
            String definitionId,
            String questionId,
            String comparisonId,
            String optionId
    );
}
