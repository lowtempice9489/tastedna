-- TasteDNA SIMPLE Q6 실제 소유 의향 관측
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- 선택한 의류 ID만 직접 관측
-- 채점 규칙과 RESULT 연결은 아직 미확정

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
    jt.option_id,
    JSON_OBJECT(
            'status', 'PENDING'
    ),
    JSON_OBJECT(
            'type', 'DIRECT_OWNERSHIP_PREFERENCE',
            'observedValue', jt.option_id,
            'evidence', 'EXPLICIT_SELECTION'
    )
FROM td_question_definition q
         JOIN JSON_TABLE(
        q.question_payload,
        '$.options[*]'
            COLUMNS (
        option_id VARCHAR(40) PATH '$.id'
    )
              ) AS jt
WHERE q.definition_id =
      '22222222-2222-4222-8222-222222222222'
  AND q.question_id = 'Q06';
