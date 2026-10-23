-- TasteDNA SIMPLE Q7 피하고 싶은 착장 조합
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- NONE은 다른 선택지와 함께 선택할 수 없음

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
           'Q07',
           7,
           JSON_OBJECT(
                   'kind', 'MULTIPLE_CHOICE',
                   'prompt', '내 옷으로는 피하고 싶은 것을 골라주세요.',
                   'description', '다른 사람에게 잘 어울리는지는 생각하지 말고, 내가 입는다고 생각해주세요.',
                   'maxSelections', 2,
                   'exclusiveOptionIds', JSON_ARRAY('NONE'),
                   'options', JSON_ARRAY(
                           JSON_OBJECT('id', 'A', 'label', '착장 A'),
                           JSON_OBJECT('id', 'B', 'label', '착장 B'),
                           JSON_OBJECT('id', 'C', 'label', '착장 C'),
                           JSON_OBJECT('id', 'D', 'label', '착장 D'),
                           JSON_OBJECT('id', 'E', 'label', '착장 E'),
                           JSON_OBJECT('id', 'F', 'label', '착장 F'),
                           JSON_OBJECT(
                                   'id', 'NONE',
                                   'label', '딱히 피하고 싶은 옷이 없어요'
                           )
                              )
           ),
           JSON_OBJECT(),
           TRUE
       );
