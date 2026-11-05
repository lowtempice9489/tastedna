-- 여성복 Simple Q05·Q06·Q10 INSERT 준비 초안 (09-D-2)
-- 실행 SQL이 아니다. 모든 INSERT는 의도적으로 일반 주석 안에 둔다.
-- Flyway 배포·DB 적용·정의 활성화 금지. 기존 정의를 UPDATE/UPSERT하지 않는다.
-- 근거: womens_simple_q05/q06/q10_draft.json 및 docs/womens-simple-db-contract-review.md
-- question_payload에는 확정된 kind/maxSelections/options(id/images)만 포함한다.
-- 적용 전 prompt/description/각 option.label, 최소 선택·필수 여부, NONE 및 배타 처리 승인 필요.
-- 관측 의미·점수·RESULT 규칙을 생성하지 않는다. 관측 테이블 INSERT도 포함하지 않는다.
-- 신규 여성복 부모 정의의 ID·버전 메타데이터 및 나머지 7문항 계약은 별도 준비가 필요하다.
-- {{...}}는 승인 후 교체할 템플릿 표식이며 SQL 값이나 실행 가능한 기본값이 아니다.
-- REQUIRED를 TRUE/FALSE로 임의 확정하지 않으며 dependency_payload도 적용 시 확인한다.
-- 주석 해제만으로 배포하지 말 것: JSON 표시문구 등 누락 계약을 먼저 완성해야 한다.

/* DRAFT Q05
INSERT INTO td_question_definition (
    definition_id, question_id, display_order, question_payload, dependency_payload, required
) VALUES (
    {{APPROVED_WOMENS_DEFINITION_ID}},
    'Q05',
    5,
    '{"kind":"MULTIPLE_CHOICE","maxSelections":3,"options":[{"id":"A","images":["/assets/gender/female/Q05/A.png"]},{"id":"B","images":["/assets/gender/female/Q05/B.png"]},{"id":"C","images":["/assets/gender/female/Q05/C.png"]},{"id":"D","images":["/assets/gender/female/Q05/D.png"]},{"id":"E","images":["/assets/gender/female/Q05/E.png"]},{"id":"F","images":["/assets/gender/female/Q05/F.png"]},{"id":"G","images":["/assets/gender/female/Q05/G.png"]},{"id":"H","images":["/assets/gender/female/Q05/H.png"]},{"id":"I","images":["/assets/gender/female/Q05/I.png"]},{"id":"J","images":["/assets/gender/female/Q05/J.png"]}]}',
    {{APPROVED_Q05_DEPENDENCY_PAYLOAD}},
    {{APPROVED_Q05_REQUIRED}}
);
*/

/* DRAFT Q06
INSERT INTO td_question_definition (
    definition_id, question_id, display_order, question_payload, dependency_payload, required
) VALUES (
    {{APPROVED_WOMENS_DEFINITION_ID}},
    'Q06',
    6,
    '{"kind":"MULTIPLE_CHOICE","maxSelections":3,"options":[{"id":"A","images":["/assets/gender/female/Q06/A.png"]},{"id":"B","images":["/assets/gender/female/Q06/B.png"]},{"id":"C","images":["/assets/gender/female/Q06/C.png"]},{"id":"D","images":["/assets/gender/female/Q06/D.png"]},{"id":"E","images":["/assets/gender/female/Q06/E.png"]},{"id":"F","images":["/assets/gender/female/Q06/F.png"]},{"id":"G","images":["/assets/gender/female/Q06/G.png"]},{"id":"H","images":["/assets/gender/female/Q06/H.png"]}]}',
    {{APPROVED_Q06_DEPENDENCY_PAYLOAD}},
    {{APPROVED_Q06_REQUIRED}}
);
*/

/* DRAFT Q10
INSERT INTO td_question_definition (
    definition_id, question_id, display_order, question_payload, dependency_payload, required
) VALUES (
    {{APPROVED_WOMENS_DEFINITION_ID}},
    'Q10',
    10,
    '{"kind":"MULTIPLE_CHOICE","maxSelections":6,"options":[{"id":"A","images":["/assets/gender/female/Q10/A.png"]},{"id":"B","images":["/assets/gender/female/Q10/B.png"]},{"id":"C","images":["/assets/gender/female/Q10/C.png"]},{"id":"D","images":["/assets/gender/female/Q10/D.png"]},{"id":"E","images":["/assets/gender/female/Q10/E.png"]},{"id":"F","images":["/assets/gender/female/Q10/F.png"]},{"id":"G","images":["/assets/gender/female/Q10/G.png"]},{"id":"H","images":["/assets/gender/female/Q10/H.png"]},{"id":"I","images":["/assets/gender/female/Q10/I.png"]},{"id":"J","images":["/assets/gender/female/Q10/J.png"]},{"id":"K","images":["/assets/gender/female/Q10/K.png"]},{"id":"L","images":["/assets/gender/female/Q10/L.png"]},{"id":"M","images":["/assets/gender/female/Q10/M.png"]},{"id":"N","images":["/assets/gender/female/Q10/N.png"]},{"id":"O","images":["/assets/gender/female/Q10/O.png"]},{"id":"P","images":["/assets/gender/female/Q10/P.png"]},{"id":"Q","images":["/assets/gender/female/Q10/Q.png"]},{"id":"R","images":["/assets/gender/female/Q10/R.png"]},{"id":"S","images":["/assets/gender/female/Q10/S.png"]}]}',
    {{APPROVED_Q10_DEPENDENCY_PAYLOAD}},
    {{APPROVED_Q10_REQUIRED}}
);
*/

