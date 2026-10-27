package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.ComparisonCandidateEvidence;
import com.tastedna.scoring.domain.ComparisonCandidateEvidenceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Q08에서 선택한 속성값에 대응하는 RESULT 후보 근거를 조회한다.
public interface ComparisonCandidateEvidenceRepository
        extends JpaRepository<
        ComparisonCandidateEvidence,
        ComparisonCandidateEvidenceId
        > {

    // 특정 비교에서 실제 선택된 속성값과 일치하는 후보 매핑을 조회한다.
    List<ComparisonCandidateEvidence>
    findByIdDefinitionIdAndIdQuestionIdAndIdComparisonIdAndIdAttributeKeyAndIdAttributeValue(
            String definitionId,
            String questionId,
            String comparisonId,
            String attributeKey,
            String attributeValue
    );
}
