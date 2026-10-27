-- RESULT 후보의 기본 식별 정보.
-- 후보 목록에 등록되었다는 사실은 최종 판별 가능성을 의미하지 않는다.
CREATE TABLE td_result_candidate (
                                     candidate_id VARCHAR(40) NOT NULL,
                                     candidate_name VARCHAR(120) NOT NULL,
                                     candidate_group VARCHAR(40) NOT NULL,
                                     verification_status VARCHAR(24) NOT NULL DEFAULT 'CANDIDATE',

                                     PRIMARY KEY (candidate_id),

                                     CONSTRAINT chk_result_candidate_verification
                                         CHECK (
                                             verification_status IN (
                                                                     'CANDIDATE',
                                                                     'VERIFIED',
                                                                     'REJECTED'
                                                 )
                                             )
);

-- RESULT v1.0의 Style Core 기본 후보 12개.
-- 현재는 직접 선호 후보로 유지하되 최종 분류 규칙은 미검증 상태다.
INSERT INTO td_result_candidate (
    candidate_id,
    candidate_name,
    candidate_group,
    verification_status
)
VALUES
    ('R01', 'Minimal', 'STYLE_CORE', 'CANDIDATE'),
    ('R02', 'Modern', 'STYLE_CORE', 'CANDIDATE'),
    ('R03', 'Classic', 'STYLE_CORE', 'CANDIDATE'),
    ('R04', 'Preppy', 'STYLE_CORE', 'CANDIDATE'),
    ('R05', 'Casual', 'STYLE_CORE', 'CANDIDATE'),
    ('R06', 'Sporty', 'STYLE_CORE', 'CANDIDATE'),
    ('R07', 'Street', 'STYLE_CORE', 'CANDIDATE'),
    ('R08', 'Vintage', 'STYLE_CORE', 'CANDIDATE'),
    ('R09', 'Romantic', 'STYLE_CORE', 'CANDIDATE'),
    ('R10', 'Bohemian', 'STYLE_CORE', 'CANDIDATE'),
    ('R11', 'Edge', 'STYLE_CORE', 'CANDIDATE'),
    ('R12', 'Glam', 'STYLE_CORE', 'CANDIDATE');
