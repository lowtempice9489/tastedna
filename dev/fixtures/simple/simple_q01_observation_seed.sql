-- TasteDNA SIMPLE Q1 직접 색상 선호 관측 규칙
-- 대상: SIMPLE-V1-DRAFT
-- 확정되지 않은 점수는 생성하지 않음
-- 이 파일은 개발용 초안 데이터 등록에 사용

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
            'type', 'DIRECT_COLOR_PREFERENCE',
            'observedValue', jt.option_label,
            'evidence', 'EXPLICIT_SELECTION'
    )
FROM td_question_definition q
         JOIN JSON_TABLE(
        q.question_payload,
        '$.options[*]'
            COLUMNS (
        option_id VARCHAR(40) PATH '$.id',
        option_label VARCHAR(255) PATH '$.label'
    )
              ) AS jt
WHERE q.definition_id =
      '22222222-2222-4222-8222-222222222222'
  AND q.question_id = 'Q01';