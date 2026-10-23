package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.OptionScoringRule;
import com.tastedna.scoring.domain.OptionScoringRuleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 선택지별 관측·점수 규칙을 DB에서 조회하는 JPA Repository다.
// OptionScoringRule 엔티티와 복합 키를 사용하며, 실제 RESULT 점수 계산은 담당하지 않는다.
public interface OptionScoringRuleRepository
        extends JpaRepository<OptionScoringRule, OptionScoringRuleId> {

    // 지정한 테스트 정의와 질문에 등록된 선택지 규칙을 모두 조회한다.
    // 선택된 옵션만 추리는 작업과 규칙 누락 검사는 SelectedOptionRuleResolver가 담당한다.
    List<OptionScoringRule> findByIdDefinitionIdAndIdQuestionId(
            String definitionId,
            String questionId
    );
}
