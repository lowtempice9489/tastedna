-- TasteDNA SIMPLE Q6 실제 소유 의향
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- 내부 취향 방향과 RESULT 점수는 연결하지 않음

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
           'Q06',
           6,
           JSON_OBJECT(
                   'kind', 'SINGLE_CHOICE',
                   'prompt', '이 중 하나를 실제로 내 옷으로 고른다면 어떤 옷을 선택하시겠어요?',
                   'description', '가격과 브랜드는 모두 비슷하다고 생각하고 골라주세요.',
                   'options', JSON_ARRAY(
                           JSON_OBJECT('id', 'A', 'label', '의류 A'),
                           JSON_OBJECT('id', 'B', 'label', '의류 B'),
                           JSON_OBJECT('id', 'C', 'label', '의류 C'),
                           JSON_OBJECT('id', 'D', 'label', '의류 D'),
                           JSON_OBJECT('id', 'E', 'label', '의류 E'),
                           JSON_OBJECT('id', 'F', 'label', '의류 F')
                              )
           ),
           JSON_OBJECT(),
           TRUE
       );
