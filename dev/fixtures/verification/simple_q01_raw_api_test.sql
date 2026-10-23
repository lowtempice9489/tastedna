-- TasteDNA Q1 Raw 관측 API 검증용 데이터
-- 개발 DB 전용 / 실제 사용자 진단 결과 아님

USE tastedna;

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @raw_test_session_id =
    '33333333-3333-4333-8333-333333333333';

SET @raw_test_definition_id =
    '22222222-2222-4222-8222-222222222222';

START TRANSACTION;

-- Q1만 가진 별도 검증 세션
-- ABANDONED 상태로 기존 활성 세션과 충돌 방지
INSERT INTO td_test_session (
    session_id,
    mall_member_id,
    definition_id,
    apparel_scope,
    status,
    revision,
    question_order,
    last_seen_at,
    lease_until,
    started_at
)
SELECT
    @raw_test_session_id,
    'dev-member-001',
    @raw_test_definition_id,
    'MENS',
    'ABANDONED',
    1,
    JSON_ARRAY('Q01'),
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
    WHERE NOT EXISTS (
    SELECT 1
    FROM td_test_session
    WHERE session_id = @raw_test_session_id COLLATE utf8mb4_unicode_ci
);

-- 실제 질문 정의의 JSON을 그대로 보존
INSERT INTO td_session_answer (
    session_id,
    question_id,
    answer_payload,
    presented_payload,
    answer_revision,
    answered_at
)
SELECT
    @raw_test_session_id,
    q.question_id,
    JSON_OBJECT(
            'selectedOptionIds',
            JSON_ARRAY('A', 'F')
    ),
    q.question_payload,
    1,
    UTC_TIMESTAMP(6)
FROM td_question_definition q
WHERE q.definition_id = @raw_test_definition_id
  AND q.question_id = 'Q01'
  AND NOT EXISTS (
    SELECT 1
    FROM td_session_answer a
    WHERE a.session_id = @raw_test_session_id COLLATE utf8mb4_unicode_ci
      AND a.question_id = 'Q01'
);

COMMIT;

-- 등록 결과 확인
SELECT
    s.session_id,
    s.mall_member_id,
    s.status,
    a.question_id,
    a.answer_payload
FROM td_test_session s
         JOIN td_session_answer a
              ON s.session_id = a.session_id
WHERE s.session_id = @raw_test_session_id COLLATE utf8mb4_unicode_ci;