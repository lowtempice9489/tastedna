-- TasteDNA SIMPLE Q4 패턴·그래픽 선호
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- 기존 DEV-SIMPLE-001은 변경하지 않음

USE tastedna;

INSERT INTO td_question_definition (
    definition_id,
    question_id,
    display_order,
    question_payload,
    dependency_payload,
    required
)
VALUES (
           '22222222-2222-4222-8222-222222222222',
           'Q04',
           4,
           JSON_OBJECT(
                   'kind', 'MULTIPLE_CHOICE',
                   'prompt', '옷에 들어간 패턴이나 그래픽 중 마음에 드는 것을 골라주세요.',
                   'description', '평소 좋아하거나 실제로 입고 싶은 것을 선택해주세요.',
                   'maxSelections', 4,
                   'options', JSON_ARRAY(
                           JSON_OBJECT('id', 'P01', 'label', '무지·솔리드'),
                           JSON_OBJECT('id', 'P02', 'label', '스트라이프'),
                           JSON_OBJECT('id', 'P03', 'label', '체크·격자'),
                           JSON_OBJECT('id', 'P04', 'label', '작은 로고·그래픽'),
                           JSON_OBJECT('id', 'P05', 'label', '큰 그래픽·레터링'),
                           JSON_OBJECT('id', 'P06', 'label', '플라워·식물 패턴'),
                           JSON_OBJECT('id', 'P07', 'label', '도트·반복 패턴'),
                           JSON_OBJECT('id', 'P08', 'label', '추상·아트 패턴'),
                           JSON_OBJECT('id', 'P09', 'label', '애니멀 패턴')
                              )
           ),
           JSON_OBJECT(),
           TRUE
       );
