-- TasteDNA 실제 SIMPLE 질문 정의 준비
-- 기존 DEV-SIMPLE-001 보호
-- 신규 버전은 비활성 상태로 등록

USE tastedna;

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
           '22222222-2222-4222-8222-222222222222',
           'SIMPLE',
           'SIMPLE-V1-DRAFT',
           10,
           'SCORING-PENDING',
           'RAW-PENDING',
           NULL,
           UTC_TIMESTAMP(6),
           FALSE
       );
