-- TasteDNA SIMPLE Q10 최종 전체 착장 선호
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- A~F 중 정확히 하나 선택
-- Q1~Q9 응답을 덮어쓰지 않음
-- RESULT 점수는 아직 연결하지 않음

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
           'Q10',
           10,
           JSON_OBJECT(
                   'kind', 'SINGLE_CHOICE',
                   'prompt', '마지막으로, 지금 가장 내 취향에 가깝다고 느껴지는 착장을 골라주세요.',
                   'description', '오래 고민하기보다 전체적인 느낌을 보고 선택해주세요.',
                   'options', JSON_ARRAY(
                           JSON_OBJECT('id', 'A', 'label', '착장 A'),
                           JSON_OBJECT('id', 'B', 'label', '착장 B'),
                           JSON_OBJECT('id', 'C', 'label', '착장 C'),
                           JSON_OBJECT('id', 'D', 'label', '착장 D'),
                           JSON_OBJECT('id', 'E', 'label', '착장 E'),
                           JSON_OBJECT('id', 'F', 'label', '착장 F')
                              )
           ),
           JSON_OBJECT(),
           TRUE
       );
