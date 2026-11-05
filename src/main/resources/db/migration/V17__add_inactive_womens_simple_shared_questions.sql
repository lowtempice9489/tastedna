-- 승인된 남녀 공통 Q01~Q04만 등록한다. Q05~Q10 완성 전에는 활성화하지 않는다.
-- 기존 초안의 ID/버전을 재사용하며 question_count는 SIMPLE의 예정 문항 수 10이다.
INSERT INTO td_test_definition
    (definition_id, test_mode, version_code, question_count, scoring_version, raw_schema_version, published_at, active)
SELECT '6acc799d-1348-4f34-b756-593e9f5bfd52', test_mode, 'SIMPLE-WOMENS-IMAGE-V1',
       question_count, scoring_version, raw_schema_version, UTC_TIMESTAMP(6), FALSE
FROM td_test_definition
WHERE definition_id = '33333333-3333-4333-8333-333333333333';

INSERT INTO td_question_definition
    (definition_id, question_id, display_order, question_payload, dependency_payload, required)
SELECT '6acc799d-1348-4f34-b756-593e9f5bfd52', question_id, display_order,
       question_payload, dependency_payload, required
FROM td_question_definition
WHERE definition_id = '33333333-3333-4333-8333-333333333333'
  AND question_id IN ('Q01', 'Q02', 'Q03', 'Q04');

-- 이미 승인된 공통 직접 관측을 그대로 복사하며 점수/새 관측 의미는 만들지 않는다.
INSERT INTO td_option_scoring_rule
    (definition_id, question_id, option_id, scoring_payload, observation_payload)
SELECT '6acc799d-1348-4f34-b756-593e9f5bfd52', question_id, option_id, scoring_payload, observation_payload
FROM td_option_scoring_rule
WHERE definition_id = '33333333-3333-4333-8333-333333333333'
  AND question_id IN ('Q01', 'Q02', 'Q03', 'Q04');
