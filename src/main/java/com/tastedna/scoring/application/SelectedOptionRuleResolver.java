package com.tastedna.scoring.application;

import com.tastedna.scoring.domain.OptionScoringRule;
import com.tastedna.scoring.persistence.OptionScoringRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 사용자가 선택한 옵션에 대응하는 관측 규칙을 DB에서 조회한다.
// 규칙 누락과 중복 선택을 차단하며, RESULT 점수 계산이나 최종 판별은 수행하지 않는다.
@Service
@RequiredArgsConstructor
public class SelectedOptionRuleResolver {

    private final OptionScoringRuleRepository ruleRepository;

    // 테스트 정의 ID와 질문 ID를 기준으로 선택된 옵션의 관측 규칙을 찾아 반환한다.
    // 읽기 전용 처리이며, 유효한 선택지가 없으면 조회를 진행하지 않는다.
    @Transactional(readOnly = true)
    public Map<String, OptionScoringRule> resolve(
            String definitionId,
            String questionId,
            List<String> selectedOptionIds
    ) {
        if (selectedOptionIds == null
                || selectedOptionIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "INVALID_SELECTED_OPTIONS"
            );
        }

        // 해당 테스트 정의와 질문에 등록된 관측 규칙을 조회한다.
        // 사용자가 선택한 옵션과 연결할 규칙을 찾기 위한 기준 데이터다.
        List<OptionScoringRule> rules =
                ruleRepository.findByIdDefinitionIdAndIdQuestionId(
                        definitionId,
                        questionId
                );

        Map<String, OptionScoringRule> available =
                new LinkedHashMap<>();

        for (OptionScoringRule rule : rules) {
            available.put(
                    rule.getId().getOptionId(),
                    rule
            );
        }

        Map<String, OptionScoringRule> resolved =
                new LinkedHashMap<>();

        // 실제 선택된 옵션만 관측 대상으로 연결한다.
        // 선택한 옵션에 규칙이 없거나 동일 옵션이 중복되면 오류로 처리한다.
        for (String optionId : selectedOptionIds) {

            OptionScoringRule rule = available.get(optionId);

            if (rule == null) {
                throw new IllegalStateException(
                        "SCORING_RULE_NOT_FOUND"
                );
            }

            if (resolved.putIfAbsent(optionId, rule) != null) {
                throw new IllegalArgumentException(
                        "DUPLICATE_SELECTED_OPTION"
                );
            }
        }

        // 호출한 코드가 반환된 규칙 목록의 구성을 변경하지 못하도록 보호한다.
        // 선택지 입력 순서를 유지한 상태로 수정 불가능한 Map을 반환한다.
        return java.util.Collections.unmodifiableMap(resolved);
    }
}
