
-- TasteDNA 로컬 개발 전용 데이터
-- 실제 TasteDNA 질문 및 판별에 사용 금지
-- 최초 1회 실행 / Flyway 마이그레이션 아님

USE tastedna;

START TRANSACTION;

INSERT INTO td_test_definition (
    definition_id,
    test_mode,
    version_code,
    question_count,
    scoring_version,
    raw_schema_version,
    equivalence_version,
    published_at,
    active
)
VALUES (
           '11111111-1111-4111-8111-111111111111',
           'SIMPLE',
           'DEV-SIMPLE-001',
           10,
           'DEV-NOT-FOR-SCORING',
           'DEV-NOT-FOR-RESULT',
           NULL,
           UTC_TIMESTAMP(6),
           TRUE
       );

INSERT INTO td_question_definition (
    definition_id,
    question_id,
    display_order,
    question_payload,
    dependency_payload,
    required
)
VALUES
    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q01', 1,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 01'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q02', 2,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 02'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q03', 3,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 03'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q04', 4,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 04'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q05', 5,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 05'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q06', 6,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 06'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q07', 7,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 07'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q08', 8,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 08'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q09', 9,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 09'),
     JSON_OBJECT(), TRUE),

    ('11111111-1111-4111-8111-111111111111',
     'DEV_Q10', 10,
     JSON_OBJECT('kind','DEV_PLACEHOLDER','label','개발용 질문 10'),
     JSON_OBJECT(), TRUE);

COMMIT;
