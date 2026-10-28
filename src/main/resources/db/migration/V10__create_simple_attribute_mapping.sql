-- Simple RESULT 후보의 실험용 속성 지지 근거.
-- 실제 관측과 비교할 수 있지만 최종 분류를 확정하는 규칙은 아니다.
CREATE TABLE td_simple_attribute_mapping (
                                             definition_id VARCHAR(36) NOT NULL,
                                             candidate_id VARCHAR(40) NOT NULL,
                                             question_id VARCHAR(20) NOT NULL,
                                             option_id VARCHAR(40) NOT NULL,
                                             reason VARCHAR(500) NOT NULL,
                                             verification_status VARCHAR(24) NOT NULL DEFAULT 'EXPERIMENTAL',

                                             PRIMARY KEY (
                                                          definition_id,
                                                          candidate_id,
                                                          question_id,
                                                          option_id
                                                 ),

                                             CONSTRAINT fk_simple_attribute_candidate
                                                 FOREIGN KEY (candidate_id)
                                                     REFERENCES td_result_candidate(candidate_id),

                                             CONSTRAINT chk_simple_attribute_status
                                                 CHECK (verification_status IN ('EXPERIMENTAL', 'VERIFIED'))
);

-- 기존 v0.1 파일에 정의된 제안 근거 네 개만 이전한다.
-- 현재 네 항목 모두 EXPERIMENTAL이며 검증된 후보 우세 근거가 아니다.
INSERT INTO td_simple_attribute_mapping (
    definition_id,
    candidate_id,
    question_id,
    option_id,
    reason,
    verification_status
)
VALUES
    (
        '22222222-2222-4222-8222-222222222222',
        'R01', 'Q04', 'P01',
        '무지·솔리드 선호는 장식이 적은 스타일과 연결될 가능성이 있다.',
        'EXPERIMENTAL'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R01', 'Q03', 'S02',
        '레귤러핏은 단정한 미니멀 스타일과 양립할 수 있다.',
        'EXPERIMENTAL'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R02', 'Q03', 'S05',
        '짧고 컴팩트한 실루엣은 구조적 비율을 강조하는 모던 스타일의 한 표현이 될 수 있다.',
        'EXPERIMENTAL'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'R02', 'Q04', 'P01',
        '무지·솔리드 선호는 모던 스타일에서도 나타날 수 있으므로 단독 구별 근거는 아니다.',
        'EXPERIMENTAL'
    );