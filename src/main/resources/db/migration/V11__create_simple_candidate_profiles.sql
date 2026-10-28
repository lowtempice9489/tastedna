-- Simple RESULT 후보별 스타일 가설이다.
-- 후보 프로필 등록은 최종 RESULT 판별 승인과 다르다.
CREATE TABLE td_simple_candidate_profile (
                                             definition_id VARCHAR(36) NOT NULL,
                                             candidate_id VARCHAR(40) NOT NULL,
                                             profile_status VARCHAR(24) NOT NULL,
                                             primary_focus VARCHAR(80) NOT NULL,
                                             description VARCHAR(500) NOT NULL,

                                             PRIMARY KEY (definition_id, candidate_id),

                                             CONSTRAINT fk_simple_profile_candidate
                                                 FOREIGN KEY (candidate_id)
                                                     REFERENCES td_result_candidate(candidate_id)
);

-- 실제 선택지와 후보 사이의 공유·양립 관계.
-- COMPATIBLE은 후보 고유 속성이나 구별 근거가 아니다.
CREATE TABLE td_simple_candidate_relation (
                                              definition_id VARCHAR(36) NOT NULL,
                                              candidate_id VARCHAR(40) NOT NULL,
                                              question_id VARCHAR(20) NOT NULL,
                                              option_id VARCHAR(40) NOT NULL,
                                              relation_type VARCHAR(32) NOT NULL,
                                              note VARCHAR(500) NOT NULL,

                                              PRIMARY KEY (
                                                           definition_id, candidate_id, question_id, option_id
                                                  ),

                                              CONSTRAINT fk_simple_relation_profile
                                                  FOREIGN KEY (definition_id, candidate_id)
                                                      REFERENCES td_simple_candidate_profile(
                                                                                             definition_id, candidate_id
                                                          ),

                                              CONSTRAINT chk_simple_relation_type
                                                  CHECK (relation_type IN (
                                                                           'SHARED_SUPPORT', 'COMPATIBLE'
                                                      ))
);

-- 기존 R01·R02 프로필의 제안 상태를 유지한다.
INSERT INTO td_simple_candidate_profile (
    definition_id, candidate_id, profile_status,
    primary_focus, description
)
VALUES
    (
        '22222222-2222-4222-8222-222222222222',
        'R01', 'PROPOSED',
        'REDUCED_VISUAL_COMPLEXITY',
        '장식 요소의 절제와 전체적인 시각적 정돈을 중심으로 하는 스타일 가설'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R02', 'PROPOSED',
        'CONTEMPORARY_STRUCTURE',
        '현대적인 실루엣 구성과 비율의 의도적인 표현을 중심으로 하는 스타일 가설'
    );

-- 기존 JSON의 속성 관계 10개를 의미 변경 없이 이전한다.
INSERT INTO td_simple_candidate_relation (
    definition_id, candidate_id, question_id,
    option_id, relation_type, note
)
VALUES
    (
        '22222222-2222-4222-8222-222222222222',
        'R01', 'Q04', 'P01', 'SHARED_SUPPORT',
        '무지·솔리드 선호는 Minimal과 Modern 모두에서 나타날 수 있다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R01', 'Q03', 'S02', 'COMPATIBLE',
        '레귤러핏은 Minimal과 양립 가능하지만 고유한 특징은 아니다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R01', 'Q03', 'S03', 'COMPATIBLE',
        '릴랙스드핏도 장식이 절제된 Minimal 스타일로 구성될 수 있다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R01', 'Q03', 'S04', 'COMPATIBLE',
        '오버사이즈핏 자체만으로 Minimal 여부를 배제하거나 확정할 수 없다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R01', 'Q04', 'P04', 'COMPATIBLE',
        '작은 로고나 그래픽의 존재만으로 Minimal과 충돌한다고 볼 수 없다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R02', 'Q04', 'P01', 'SHARED_SUPPORT',
        '무지·솔리드 선호는 Modern에서도 나타날 수 있어 단독 구별 근거가 아니다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R02', 'Q03', 'S05', 'COMPATIBLE',
        '짧고 컴팩트한 실루엣은 Modern의 한 표현일 수 있지만 고유한 속성은 아니다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R02', 'Q03', 'S03', 'COMPATIBLE',
        '릴랙스드핏은 현대적인 비율 구성에도 활용될 수 있다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R02', 'Q03', 'S04', 'COMPATIBLE',
        '오버사이즈핏은 Modern에서도 나타날 수 있지만 단독 구별 근거는 아니다.'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R02', 'Q04', 'P04', 'COMPATIBLE',
        '작은 로고나 그래픽은 Modern과 양립 가능하지만 고유한 특징은 아니다.'
    );