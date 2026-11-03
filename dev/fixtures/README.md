# 개발 fixture의 용도

2026-11-03 정리 기준. 현재 서버 설정은 DB/JPA에서 읽는다. 이 디렉터리를 런타임 설정 폴더로 사용하지 않는다.

## 유지하는 자료

- simple/*.sql, local_simple_seed.sql, simple_v1_definition_seed.sql, verification/*.sql: 기존 정의·관측 데이터 재현 및 수동 검증 자료. Flyway를 대신하지 않으며 실행하면 DB를 변경하므로 자동 실행하지 않는다.
- simple의 Q01/Q02/Q05/Q07/Q08/Q09/Q10 JSON: 기존 계약 회귀 테스트에서 읽는다. Q05/Q10 및 Q08/Q09는 파일명을 동적으로 조합하는 참조도 있다.
- result의 direct_evidence, attribute_mapping, discrimination 및 Q05_Q10/Q08/Q09 metadata JSON: 알고리즘·구조 검사 및 V14 이관값 동등성 테스트의 입력이다. DB 서버 없이 실행하는 테스트용이며 런타임 원본이 아니다.
- 적용된 src/main/resources/db/migration 이력은 삭제하거나 수정하지 않는다.

## 제거한 자료

- 루트의 answer-404-test, answer-409-test, answer-null-test, answer-success-test, invalid-request, missing-revision, session-start-request, validation-test JSON: 참조되지 않는 수동 API 요청 파일 8개.
- simple_q01_observation_rules.json: 참조되지 않는 초안. 관측 등록 SQL을 유지한다.
- simple_q03_question_payload.json, simple_q04_question_payload.json, simple_q06_question_payload.json: 실행·테스트 참조가 없는 질문 초안. 각 문항의 seed SQL을 유지한다.
- simple_evidence_capability_v0.1.json, simple_outfit_option_metadata_v0.1.json, simple_style_core_candidate_profiles_v0.1.json: 실행·테스트 참조가 없는 이전 실험 메타데이터. 기존 DB 마이그레이션과 사용 중인 구체적인 메타데이터 fixture를 유지한다.

총 15개 JSON을 제거했다. DB 데이터·이미지·서비스 코드·실행 스크립트는 변경하지 않았다. 테스트용 JSON을 없애기 위해 검증 자체를 삭제하지 않는다.
