package com.tastedna.scoring.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

// Q02 직접 선택한 스타일과 RESULT 후보의 DB 매핑을 조회한다.
// 이 매핑은 직접 선호 근거이며 최종 종합 RESULT 판별 규칙이 아니다.
@Repository
public class StyleCoreDirectMappingRepository {

    private final JdbcTemplate jdbcTemplate;

    public StyleCoreDirectMappingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 질문 선택지와 RESULT 후보의 대응 관계.
    public record DirectMapping(
            String optionId,
            String candidateId
    ) {
    }

    // 테스트 정의와 질문에 해당하는 직접 선호 매핑만 읽는다.
    public List<DirectMapping> findByDefinitionAndQuestion(
            String definitionId,
            String questionId
    ) {
        return jdbcTemplate.query(
                """
                SELECT option_id, candidate_id
                FROM td_style_core_direct_mapping
                WHERE source_definition_id = ?
                  AND question_id = ?
                ORDER BY option_id
                """,
                (rs, rowNum) -> new DirectMapping(
                        rs.getString("option_id"),
                        rs.getString("candidate_id")
                ),
                definitionId,
                questionId
        );
    }
}
