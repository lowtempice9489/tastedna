-- TasteDNA SIMPLE Q3 핏·실루엣 선호
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
           'Q03',
           3,
           JSON_OBJECT(
                   'kind', 'MULTIPLE_CHOICE',
                   'prompt', '옷을 입었을 때 어떤 핏이나 실루엣이 가장 마음에 드나요?',
                   'description', '평소 좋아하거나 실제로 입고 싶은 형태를 선택해주세요.',
                   'maxSelections', 3,
                   'options', JSON_ARRAY(
                           JSON_OBJECT('id', 'S01', 'label', '슬림핏'),
                           JSON_OBJECT('id', 'S02', 'label', '레귤러핏'),
                           JSON_OBJECT('id', 'S03', 'label', '릴랙스드핏'),
                           JSON_OBJECT('id', 'S04', 'label', '오버사이즈핏'),
                           JSON_OBJECT('id', 'S05', 'label', '짧고 컴팩트한 실루엣'),
                           JSON_OBJECT('id', 'S06', 'label', '길고 여유로운 실루엣')
                              )
           ),
           JSON_OBJECT(),
           TRUE
       );
