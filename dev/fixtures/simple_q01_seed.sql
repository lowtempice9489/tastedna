-- TasteDNA SIMPLE Q1 질문 정의
-- 대상: SIMPLE-V1-DRAFT (비활성)
-- 기존 DEV-SIMPLE-001은 수정하지 않음
-- Q1만 등록하며 아직 실행하지 않음

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
           'Q01',
           1,
           JSON_OBJECT(
                   'kind', 'MULTIPLE_CHOICE',
                   'prompt', '옷을 고를 때 자연스럽게 손이 가는 색을 골라주세요.',
                   'description', '평소 자주 입는 색이 아니라, 실제로 마음이 가는 색을 기준으로 선택해주세요.',
                   'maxSelections', 5,
                   'options', JSON_ARRAY(
                           JSON_OBJECT('id', 'A', 'label', '블랙'),
                           JSON_OBJECT('id', 'B', 'label', '화이트·아이보리'),
                           JSON_OBJECT('id', 'C', 'label', '그레이·실버'),
                           JSON_OBJECT('id', 'D', 'label', '베이지·크림'),
                           JSON_OBJECT('id', 'E', 'label', '브라운·카멜'),
                           JSON_OBJECT('id', 'F', 'label', '네이비'),
                           JSON_OBJECT('id', 'G', 'label', '블루'),
                           JSON_OBJECT('id', 'H', 'label', '그린·올리브'),
                           JSON_OBJECT('id', 'I', 'label', '레드·버건디'),
                           JSON_OBJECT('id', 'J', 'label', '핑크'),
                           JSON_OBJECT('id', 'K', 'label', '퍼플'),
                           JSON_OBJECT('id', 'L', 'label', '옐로·머스터드'),
                           JSON_OBJECT('id', 'M', 'label', '오렌지·코랄'),
                           JSON_OBJECT('id', 'N', 'label', '파스텔 계열'),
                           JSON_OBJECT('id', 'O', 'label', '선명하고 밝은 색 계열')
                              )
           ),
           JSON_OBJECT(),
           TRUE
       );
