-- TasteDNA SIMPLE Q9 스타일 조합 비교 질문
-- 대상: SIMPLE-V1-DRAFT (비활성 유지)
-- 각 비교쌍에서 A/B 중 하나 선택
-- 기본 고정 비교 3개 사용
-- Q2 기반 적응형 비교 시험안은 아직 적용하지 않음
-- RESULT 점수는 연결하지 않음

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
           'Q09',
           9,
           JSON_OBJECT(
                   'kind', 'COMPARISON',
                   'prompt', '같은 취향 요소도 어떻게 조합하느냐에 따라 느낌이 달라져요.',
                   'description', '각 조합에서 실제로 더 입고 싶은 착장을 골라주세요.',
                   'comparisons', JSON_ARRAY(
                           JSON_OBJECT(
                                   'id', 'C01',
                                   'label', '비교 1',
                                   'options', JSON_ARRAY(
                                           JSON_OBJECT('id', 'A', 'label', '착장 A'),
                                           JSON_OBJECT('id', 'B', 'label', '착장 B')
                                              )
                           ),
                           JSON_OBJECT(
                                   'id', 'C02',
                                   'label', '비교 2',
                                   'options', JSON_ARRAY(
                                           JSON_OBJECT('id', 'A', 'label', '착장 A'),
                                           JSON_OBJECT('id', 'B', 'label', '착장 B')
                                              )
                           ),
                           JSON_OBJECT(
                                   'id', 'C03',
                                   'label', '비교 3',
                                   'options', JSON_ARRAY(
                                           JSON_OBJECT('id', 'A', 'label', '착장 A'),
                                           JSON_OBJECT('id', 'B', 'label', '착장 B')
                                              )
                           )
                                  )
           ),
           JSON_OBJECT(),
           TRUE
       );
