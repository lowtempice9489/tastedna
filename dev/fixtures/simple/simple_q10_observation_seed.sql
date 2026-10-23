-- TasteDNA SIMPLE Q10 최종 전체 착장 선호 관측
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- 사용자가 마지막에 직접 선택한 착장만 기록
-- 기존 Q1~Q9 관측을 덮어쓰지 않음
-- RESULT 점수 및 가중치는 아직 연결하지 않음

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
            'type', 'DIRECT_FINAL_OUTFIT_PREFERENCE',
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
  AND q.question_id = 'Q10';
