-- TasteDNA SIMPLE Q5 첫눈에 끌리는 전체 착장
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- 선택지 A~F의 내부 분석 의미는 고객에게 노출하지 않음

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
           'Q05',
           5,
           JSON_OBJECT(
                   'kind', 'SINGLE_CHOICE',
                   'prompt', '옷에 대한 설명은 잠시 잊고, 첫눈에 가장 끌리는 착장을 골라주세요.',
                   'description', '실제로 한 번 입어보고 싶은 착장을 선택해주세요.',
                   'options', JSON_ARRAY(
                           JSON_OBJECT('id', 'A', 'label', '착장 1'),
                           JSON_OBJECT('id', 'B', 'label', '착장 2'),
                           JSON_OBJECT('id', 'C', 'label', '착장 3'),
                           JSON_OBJECT('id', 'D', 'label', '착장 4'),
                           JSON_OBJECT('id', 'E', 'label', '착장 5'),
                           JSON_OBJECT('id', 'F', 'label', '착장 6')
                              )
           ),
           JSON_OBJECT(),
           TRUE
       );
