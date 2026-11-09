-- RESULT 설계 v1.0의 추가 작업용 후보를 등록한다.
-- 후보 등록은 판별 규칙 검증이나 최종 RESULT 승인이 아니다.
-- 기존 R01~R12 및 저장된 회원 응답은 변경하지 않는다.

INSERT INTO td_result_candidate (
    candidate_id,
    candidate_name,
    candidate_group,
    verification_status
)
VALUES
    ('R13', 'Graphic Street',       'STYLE_VARIANT',      'CANDIDATE'),
    ('R14', 'Pattern Vintage',      'STYLE_VARIANT',      'CANDIDATE'),
    ('R15', 'Expressive Romantic',  'STYLE_VARIANT',      'CANDIDATE'),

    ('R16', 'Preppy×Casual',         'STYLE_INTERACTION',  'CANDIDATE'),
    ('R17', 'Sporty×Casual',         'STYLE_INTERACTION',  'CANDIDATE'),
    ('R18', 'Minimal×Street',        'STYLE_INTERACTION',  'CANDIDATE'),
    ('R19', 'Vintage×Casual',        'STYLE_INTERACTION',  'CANDIDATE'),
    ('R20', 'Romantic×Edge',         'STYLE_INTERACTION',  'CANDIDATE'),

    ('R21', 'Color-led',            'ATTRIBUTE_LED',      'CANDIDATE'),
    ('R22', 'Silhouette-led',       'ATTRIBUTE_LED',      'CANDIDATE'),
    ('R23', 'Surface-led',          'ATTRIBUTE_LED',      'CANDIDATE'),

    ('R24', 'Vintage×Edge',         'ADDITIONAL_REVIEW',  'CANDIDATE'),
    ('R25', 'Classic×Street',       'ADDITIONAL_REVIEW',  'CANDIDATE');
