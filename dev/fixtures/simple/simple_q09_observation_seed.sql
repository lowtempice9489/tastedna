-- TasteDNA SIMPLE Q9 스타일 조합 선호 관측
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- 내부 관측 규칙 ID: comparisonId:selectedOptionId
-- Q8의 개별 요소 비교 관측과 구분
-- RESULT 점수는 아직 연결하지 않음

USE tastedna;

INSERT INTO td_option_scoring_rule (
    definition_id,
    question_id,
    option_id,
    scoring_payload,
    observation_payload
)
SELECT
    q.definition_id,
    q.question_id,
    CONCAT(
            jt.comparison_id,
            ':',
            jt.option_id
    ),
    JSON_OBJECT(
            'status', 'PENDING'
    ),
    JSON_OBJECT(
            'type', 'DIRECT_COMBINATION_PREFERENCE',
            'observedValue', CONCAT(
                    jt.comparison_id,
                    ':',
                    jt.option_id
                             ),
            'evidence', 'EXPLICIT_SELECTION'
    )
FROM td_question_definition q
         JOIN JSON_TABLE(
        q.question_payload,
        '$.comparisons[*]'
            COLUMNS (
        comparison_id VARCHAR(40) PATH '$.id',
        NESTED PATH '$.options[*]'
        COLUMNS (
            option_id VARCHAR(40) PATH '$.id'
        )
    )
              ) AS jt
WHERE q.definition_id =
      '22222222-2222-4222-8222-222222222222'
  AND q.question_id = 'Q09';
