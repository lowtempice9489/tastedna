-- TasteDNA SIMPLE Q8 통제 비교 질문
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- 각 비교쌍에서 A/B 중 하나를 선택
-- 비교쌍 ID와 선택지 ID를 함께 보존
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
           'Q08',
           8,
           JSON_OBJECT(
                   'kind', 'COMPARISON',
                   'prompt', '거의 비슷한 두 옷 중 더 마음에 드는 쪽을 골라주세요.',
                   'description', '한 가지 차이에 집중해서 실제로 더 입고 싶은 쪽을 선택해주세요.',
                   'comparisons', JSON_ARRAY(
                           JSON_OBJECT(
                                   'id', 'C01',
                                   'label', '비교 1',
                                   'options', JSON_ARRAY(
                                           JSON_OBJECT('id', 'A', 'label', '의류 A'),
                                           JSON_OBJECT('id', 'B', 'label', '의류 B')
                                              )
                           ),
                           JSON_OBJECT(
                                   'id', 'C02',
                                   'label', '비교 2',
                                   'options', JSON_ARRAY(
                                           JSON_OBJECT('id', 'A', 'label', '의류 A'),
                                           JSON_OBJECT('id', 'B', 'label', '의류 B')
                                              )
                           ),
                           JSON_OBJECT(
                                   'id', 'C03',
                                   'label', '비교 3',
                                   'options', JSON_ARRAY(
                                           JSON_OBJECT('id', 'A', 'label', '의류 A'),
                                           JSON_OBJECT('id', 'B', 'label', '의류 B')
                                              )
                           )
                                  )
           ),
           JSON_OBJECT(),
           TRUE
       );
