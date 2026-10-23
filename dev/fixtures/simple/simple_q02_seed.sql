-- TasteDNA SIMPLE Q2 패션룩 직접 선호
-- 대상: SIMPLE-V1-DRAFT (비활성)
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
           'Q02',
           2,
           JSON_OBJECT(
                   'kind', 'MULTIPLE_CHOICE',
                   'prompt', '내 스타일에 가깝거나, 입어보고 싶은 패션룩을 골라주세요.',
                   'description', '지금 자주 입는 스타일뿐 아니라 실제로 끌리는 스타일도 함께 선택해주세요.',
                   'maxSelections', 4,
                   'options', JSON_ARRAY(
                           JSON_OBJECT('id', 'R01', 'label', '미니멀'),
                           JSON_OBJECT('id', 'R02', 'label', '모던'),
                           JSON_OBJECT('id', 'R03', 'label', '클래식'),
                           JSON_OBJECT('id', 'R04', 'label', '프레피'),
                           JSON_OBJECT('id', 'R05', 'label', '캐주얼'),
                           JSON_OBJECT('id', 'R06', 'label', '스포티'),
                           JSON_OBJECT('id', 'R07', 'label', '스트릿'),
                           JSON_OBJECT('id', 'R08', 'label', '빈티지·레트로'),
                           JSON_OBJECT('id', 'R09', 'label', '로맨틱'),
                           JSON_OBJECT('id', 'R10', 'label', '보헤미안'),
                           JSON_OBJECT('id', 'R11', 'label', '에지·그런지'),
                           JSON_OBJECT('id', 'R12', 'label', '글램·화려함')
                              )
           ),
           JSON_OBJECT(),
           TRUE
       );
