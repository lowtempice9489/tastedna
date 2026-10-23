-- TasteDNA SIMPLE Q7 회피 착장 관측
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- A~F: 직접 선택한 회피 착장
-- NONE: 회피 대상 없음 응답
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
    jt.option_id,
    JSON_OBJECT(
            'status', 'PENDING'
    ),
    CASE
        WHEN jt.option_id = 'NONE' THEN
            JSON_OBJECT(
                    'type', 'DIRECT_NO_AVOIDED_OUTFIT',
                    'observedValue', 'NONE',
                    'evidence', 'EXPLICIT_SELECTION'
            )
        ELSE
            JSON_OBJECT(
                    'type', 'DIRECT_AVOIDED_OUTFIT',
                    'observedValue', jt.option_id,
                    'evidence', 'EXPLICIT_SELECTION'
            )
        END
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
  AND q.question_id = 'Q07';
