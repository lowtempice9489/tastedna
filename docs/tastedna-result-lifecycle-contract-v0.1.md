# TasteDNA 결과 생명주기 계약 v0.1 — RESULT-12A

기준: `feature/result-finalization`, `054d83a` 이후 작업. 기획 기록 08 원문과 이번 사용자 지시를 대조했다. 이번 구현은 **DB/JPA 모델**이며 저장·현재 설정 HTTP API, 화면, 자동 만료는 아직 없다.

## 1. 세 가지 독립된 행동

| 구분 | 보관 위치 / 정책 | 12A 구현 상태 |
|---|---|---|
| 테스트 이력 | 기존 `td_test_session`, `td_session_answer` | 그대로 유지. 제출만으로 보관 행 생성 안 함 |
| 명시적 저장 | `td_member_result_retention.saved_slot/saved_at` | 1~5 슬롯 모델과 DB 제약. 사용자 저장 경로는 12B |
| 현재 TasteDNA | 같은 행의 `current_marker/current_since` | 회원당 최대 하나. 설정·교체·해제 경로는 12B |

현재만 지정 / 저장만 지정 / 둘 다 / 둘 다 아님을 표현할 수 있다. 새 테스트는 슬롯이 가득 차도 진행할 수 있다. 새 제출로 저장하거나 현재를 교체하지 않는다. 저장 해제는 현재를 해제하지 않고, 현재 해제는 저장을 해제하지 않는다. 보호 의사가 하나라도 있으면 5일 경과와 관계없이 보존한다.

기존 마이페이지의 ‘저장된 테스트’는 `findSimpleHistory()`의 제출 이력 표현이다. 이번에 UI나 조회 API의 뜻을 변경하지 않으며, 12C에서 명시적 저장과 이력 문구/목록을 구분해야 한다.

## 2. 기존 자료와 신규 모델

기존 세션에는 회원·정의·의류 범위·상태·revision·submittedAt이 있다. 답변은 `(session_id, question_id)`당 최신 `answer_payload`, `presented_payload`, `answer_revision`을 저장한다. 제출 후 기존 서비스가 답변 수정을 금지한다. 과거 수정 이력을 별도로 저장하는 구조는 아니다.

RESULT 카탈로그는 `td_result_candidate`이며 현재 `SimpleInitialCandidateService.find()`는 저장 응답에서 Raw와 FINAL을 재계산한다. `TestSession.resultId`는 현재 FINAL 응답을 영속 보존하는 필드가 아니다. 같은 규칙 버전의 재현성과 규칙 변경 후 과거 결과 보존은 다르다.

V27은 다음 **한 테이블만 CREATE**한다. 기존 테이블 ALTER/DML, 데이터 복사, 자동 저장 전환은 없다.

| 컬럼 | 형식 | 의미 |
|---|---|---|
| session_id | CHAR(36), PK | 원본 세션. 동일 세션 중복 보관 행 방지 |
| mall_member_id | VARCHAR(128) | 기존 쇼핑몰 회원 식별자. 클라이언트가 지정하는 소유자가 아님 |
| submission_revision | BIGINT | 캡처한 제출 revision |
| rule_version | VARCHAR(255) | 캡처 시 실제 판별 버전 |
| result_snapshot | JSON | 그 시점의 서버 판별 응답 보존용. 답변 JSON 복제용이 아님 |
| captured_at | DATETIME(6) | 최초 캡처 UTC 시각 |
| saved_slot / saved_at | INT NULL / DATETIME(6) NULL | 슬롯 1~5, 명시적 저장 시각. 둘 다 NULL이면 미저장 |
| current_marker / current_since | INT NULL / DATETIME(6) NULL | marker=1이면 현재. 둘 다 NULL이면 현재 아님 |
| version | BIGINT | JPA 낙관적 잠금 버전 |

`(session_id, mall_member_id)`는 V4의 기존 `uq_td_test_session_owner`에 FK로 연결한다. 따라서 다른 회원의 세션을 자기 보관 행으로 연결할 수 없다. FK는 RESTRICT이고 cascade 삭제는 없다. 문자열 collation은 부모와 같은 `utf8mb4_unicode_ci`다. 기존 외부 쇼핑몰 식별자 경계를 유지하기 위해 FORM 회원 FK를 기존 세션에 추가하지 않는다.

DB 한도:

- UNIQUE `(mall_member_id, saved_slot)` + CHECK 1~5. NULL은 미저장으로 여러 행에 허용된다. 슬롯 번호는 순위나 자동 교체 순서가 아니다.
- UNIQUE `(mall_member_id, current_marker)` + CHECK marker=1. 회원당 현재 설정 최대 하나.
- 두 상태와 시각의 NULL 일치 CHECK. 0/6 등의 슬롯이나 다른 current marker 거부.
- PK로 같은 세션 중복 행 방지. 무보호 행 자체는 허용하지만 저장 수에 포함하지 않는다.

`MemberResultRetention`은 본인 소유의 SUBMITTED 세션만 캡처하며 revision을 복사한다. `saveToSlot/removeSaved/setCurrent/clearCurrent/isProtected`가 두 상태를 독립적으로 다룬다. JPA 필드와 제약을 제공할 뿐, 현재 설정 교체 트랜잭션이나 API를 구현한 것은 아니다.

## 3. 보관 결과 재구성과 스냅샷 경계

원본 응답과 제시된 질문은 기존 세션·답변을 참조한다. 신규 JSON은 규칙 변경으로 과거 FINAL이 달라지는 것을 막기 위해 **판별 응답만** 보존한다. 12B 최초 명시적 보호 시 서버가 계산한 `SimpleInitialCandidateService.Response` 전체를 직렬화하는 계약으로 한다. 여기에는 직접 대표, decision, 실제 후보 근거·revision 및 판별 버전이 포함된다. FINAL 여부는 저장 자격과 동일시하지 않는다. 제출된 DEFERRED 응답도 임의 FINAL로 바꾸지 않는다.

12A 생성자는 스냅샷 저장 구조만 제공한다. 응답의 sessionId/definitionId/revision/ruleVersion 일치 검증, JSON 생성, 저장 결과 우선 조회는 **12B 미구현 책임**이다. 클라이언트 JSON을 그대로 보관하면 안 된다. 기존 행 재저장·현재 전환 시 최초 스냅샷을 덮어쓰지 않는다. 과거 제출을 처음 보관할 때는 당시의 미기록 FINAL을 복원했다고 주장하지 않고, 명시적 보호 시점의 판별 버전과 캡처 시각을 기록한다.

Raw 상품 매칭은 원본 응답을 계속 사용한다. 보관 대상의 질문 정의·관측 규칙·이미지 참조도 재구성 의존성이므로 파괴적으로 변경하거나 삭제하지 않는다. 향후 규칙/질문 버전 교체 시 과거 Raw 재구성 보장 방식은 별도 검토가 필요하다. 현재 스냅샷만으로 임의의 미래 관측 엔진까지 재현된다고 보장하지 않는다.

## 4. RESULT-12B 트랜잭션 연결 위치

기존 `FormMemberRepository.findByMemberIdForUpdate()`를 추가했다. FORM 관리 서비스의 모든 쓰기는 인증된 회원 ID로 회원 행을 먼저 잠그고, 기존 `TestSessionRepository.findByIdAndMemberIdForUpdate()`로 대상 소유권·제출 상태를 확인한다. 보관 행이 아직 없는 경우에도 회원 잠금으로 동일 회원의 슬롯 배정과 현재 변경을 직렬화한다. 외부 쇼핑몰에서는 해당 인증 경계의 회원별 잠금으로 대체한다.

후속 관리 서비스 계약(이번에는 서비스/컨트롤러를 만들지 않음):

1. `save(memberId, sessionId)`: 회원 잠금 → 본인 제출 세션 조회 → 기존 보관 행 조회/최초 서버 스냅샷 캡처 → 이미 저장이면 멱등 반환 → 빈 슬롯 선택 → 없으면 저장 한도 오류. 기존 저장 결과 자동 삭제 금지.
2. `removeSaved(memberId, sessionId)`: 동일 잠금/소유권 검사 → `removeSaved()`만 수행. 현재 상태와 원본 응답을 삭제하지 않음.
3. `setCurrent(memberId, sessionId)`: 동일 잠금 → 대상 확인/캡처 → 기존 현재 행 `clearCurrent()` 후 **flush** → 대상 `setCurrent()` → 커밋. 중간 실패는 전체 롤백. 기존 UNIQUE가 있으므로 flush 순서가 중요함.
4. `clearCurrent(memberId)`: 동일 회원 잠금 → 현재 행의 설정만 해제. 저장 슬롯 보존.
5. 조회: `MemberResultRetentionRepository`의 회원 조건 메서드로 저장 목록/현재/특정 결과 조회. 기존 이력 API와 RESULT 엔진은 그대로 재사용. 새 응답 계약은 12B에서 확정.

동시성: DB UNIQUE/CHECK가 최종 한도를 지키고 회원 잠금은 정상 요청의 충돌을 줄인다. `@Version`은 동일 행의 오래된 변경을 거부한다. 실제 병렬 트랜잭션 테스트, API 오류 변환 및 계정 상태 검사는 12B/12F에서 필요하다. 현재 모델만으로 전체 동시성 서비스가 검증됐다는 뜻은 아니다.

## 5. 만료·기존 기록·향후 삭제

- 회원 임시 결과 기준은 기존 `submittedAt + 5일`이며 `saved_slot != null OR current_marker != null`이면 보호한다. `lease_until`은 진행 세션 임대이며 결과 만료 기준이 아니다.
- 현재 지정 해제나 저장 해제가 제출 시각을 갱신하지 않는다. 이미 5일을 넘긴 마지막 보호 해제 직후의 유예/고지 정책은 12D에서 확정한다.
- 기존 9개 등 모든 과거 제출은 그대로 둔다. V27은 저장 행을 자동 생성하지 않고, 기존 기록을 삭제하지 않는다. 정리 기능 도입 시 과거 기록에 대한 적용 시작일·유예 정책 없이 일괄 만료시키지 않는다.
- 비회원 12시간은 계획이다. 현재 인증 개방, 익명 소유자 ID 생성, 쿠키/토큰 및 회원 전환은 없다. 기존 세션 소유권을 빈 회원 ID로 우회하지 않는다. 12E에서 별도 소유권과 완료 시각 기준을 설계한다.
- 원문 기획 08의 Deep 재사용 약 90일과 미저장 RESULT 5일은 다르다. 현재 답변을 그대로 삭제하면 후자의 재사용 자료도 사라질 수 있으므로 최소 재사용 데이터 범위를 정하기 전 일괄 삭제를 활성화하지 않는다. Deep 구현은 이번 범위 밖이다.

12D 정리 설계 순서: 회원 단위 잠금 및 보호 재확인 → 과거 기록 유예/별도 보존 요건 확인 → 원본 참조와 관측·판별 의존성 조사 → 무보호 관리 행/파생 자료 정리 → 허용된 원본 세션·답변 정리. 현재 V4 답변 FK는 세션 삭제 시 CASCADE이므로 세션을 먼저 삭제해서는 안 된다. 보호 행의 FK는 세션 삭제를 막지만 답변 직접 삭제까지 막지는 않으므로 정리 서비스의 보호 검사가 필요하다. 화면 숨김을 실제 소멸로 보고하지 않는다.

정확한 배치 시각, 실패 재처리, 보존 의무, 계정 삭제, 과거 자료 유예, Deep 최소 재사용 자료 및 감사 기록은 아직 확정하지 않았다. **이번 작업은 정리 쿼리나 스케줄러를 실행하지 않는다.**

## 6. 검증과 남은 범위

- 단위 테스트: 소유권/제출 조건, 캡처 시 기본 미보호, 슬롯 범위, 반복 행동, 저장/현재 독립 해제, 스냅샷 유지, V27 비파괴 작성 구조.
- MySQL JPA 테스트: 별도 `RUN_RESULT_LIFECYCLE_MYSQL_TESTS=true`일 때만 실행. 대상 URL은 `tastedna_test`로 고정, 자동 Flyway는 꺼져 있으며 **V27을 별도로 적용한 DB**에서 ddl validate한다. 테스트 행은 트랜잭션 롤백한다. 기존 실제 제출을 수정하지 않는다.
- JPA 테스트 범위: 제출만으로 보관 행이 생기지 않음, 회원 조회 분리, 6번째 제출 허용/중복 슬롯 거부, 범위 밖 슬롯·중복 현재·다른 소유자 FK·중복 세션 거부. 병렬 경쟁 검증은 아직 없다.
- 12B: 관리 서비스/API, 서버 스냅샷 생성·검증·조회 연결 및 트랜잭션 동작.
- 12C: 리포트/마이페이지 저장·현재 설정 버튼과 이력/저장 목록 분리.
- 12D: 과거 데이터 보호 정책을 확정한 뒤 5일 정리와 실제 삭제 범위.
- 12E: 비회원 12시간과 회원 전환.
- 12F: 실제 MySQL 제약/경쟁, 브라우저 전체 관리 흐름.

12A 실행 결과: Java 21 컴파일 성공. `MemberResultRetentionTest` 5개, `TestSessionTest` 5개, `TestSessionServiceTest` 38개, `SimpleInitialCandidateServiceTest` 69개, 총 117개 통과. `MemberResultRetentionJpaTest` 6개는 환경변수/DB 자격 증명이 없어 건너뜀. 실제 MySQL 적용·제약 검증 및 브라우저는 미실시다. 기존 `MemberAuthMysqlIntegrationTest`의 V25 고정 assertion은 저장소의 최신 마이그레이션 버전·개수를 대조하도록 보완했으며 실제 실행하지 않았다.
