-- Q02에서 직접 선택한 스타일을 RESULT 후보에 연결하는 규칙.
-- 직접 선호 매핑이며, 최종 종합 RESULT 판별 규칙은 아니다.
CREATE TABLE td_style_core_direct_mapping (
                                              source_definition_id VARCHAR(36) NOT NULL,
                                              question_id VARCHAR(20) NOT NULL,
                                              option_id VARCHAR(40) NOT NULL,
                                              candidate_id VARCHAR(40) NOT NULL,

                                              PRIMARY KEY (
                                                           source_definition_id,
                                                           question_id,
                                                           option_id
                                                  ),

                                              CONSTRAINT fk_style_core_mapping_candidate
                                                  FOREIGN KEY (candidate_id)
                                                      REFERENCES td_result_candidate(candidate_id)
);

-- 기존 Simple Q02의 직접 선호 매핑 12개를 DB에 등록한다.
INSERT INTO td_style_core_direct_mapping (
    source_definition_id,
    question_id,
    option_id,
    candidate_id
)
VALUES
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R01', 'R01'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R02', 'R02'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R03', 'R03'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R04', 'R04'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R05', 'R05'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R06', 'R06'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R07', 'R07'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R08', 'R08'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R09', 'R09'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R10', 'R10'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R11', 'R11'),
    ('22222222-2222-4222-8222-222222222222', 'Q02', 'R12', 'R12');