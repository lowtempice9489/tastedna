# TasteDNA 결과 생명주기 계약 v0.1 — RESULT-12C 현행 갱신

기준 브랜치 `feature/result-finalization`, 작업 시작 HEAD `e17164eed13b7c5e5d8a280ab2cba5d4190348d7`.
기획 기록 08과 이후 사용자 결정 중 **RESULT-12C 지시가 현행 정책**이다. 문서 경로는 유지한다.

설계 이력: 12A에서 V27/JPA 보관 모델을 도입했고, 12B에서 회원 관리 API·FORM 화면과 비회원 요청 내 계산을 연결했다. `affe7d7`의 FINAL 신규 현재 설정 제한 및 `e17164e`의 미확정 설정 버튼 숨김을 유지한다. 12C는 신규 회원 제출의 5일 만료 **판별·검토 대상 조회**를 추가한다. 물리 삭제, 스케줄러, 만료 결과의 화면 숨김 또는 조회·저장 차단은 구현하지 않는다.

## 1. 현행 정책과 구현 상태

| 개념 | 정책 | 현행 구현 |
|---|---|---|
| 테스트 이력 | 제출 ≠ 저장 ≠ 현재 설정 | 기존 세션·답변 유지, 제출만으로 보관 행/슬롯/현재 설정을 만들지 않음 |
| 명시적 저장 | 회원당 최대 5개, 직접 저장·해제 | V27 슬롯 제약 + 회원 잠금 관리 API/화면. 가득 차도 새 테스트 허용, 자동 교체 없음 |
| 현재 TasteDNA | 회원당 최대 1개, 직접 설정·변경·해제 | 저장과 독립적. 새 제출로 자동 변경하지 않음 |
| FINAL 미확정 결과 | 저장 가능, 신규 현재 설정 금지 | 서버 409 `CURRENT_TASTEDNA_REQUIRES_FINAL`, 화면 설정 버튼 미노출. 과거 미확정 현재 기록은 자동 변경하지 않음 |
| 회원 미보호 완료 결과 | 제출부터 5일 | 신규 적용 표식이 있는 세션만 만료 판별. 저장 또는 현재면 보호 |
| 비회원 | 테스트·제출 직후 결과 확인만 가능, 보존 없음 | 남녀 Simple 요청 내 메모리 계산. 회원 세션/답변/보관 테이블에 쓰지 않음 |

리포트와 마이페이지는 현재 TasteDNA / 명시적 저장 / 최근 테스트 이력을 구분한다. 과거 ‘저장된 테스트 9개’는 제출 이력이었으며, 9개를 명시적 저장으로 자동 전환하지 않는다. 저장만/현재만/둘 다/둘 다 아님을 표현할 수 있다.

## 2. 데이터와 참조 관계

- `td_test_session`: 회원, 정의, 의류 범위, 상태, revision, submitted_at, V28의 temporary_result_expires_at.
- `td_session_answer`: `(session_id, question_id)`별 최신 `answer_payload`, 당시 `presented_payload`, `answer_revision`. 수정 이력 전체를 보존하는 테이블은 아니며 제출 뒤 답변 수정은 기존 서비스가 금지한다.
- `td_result_candidate`: R01~R25 카탈로그. 이번 작업은 카탈로그와 판별 규칙을 변경하지 않는다.
- Raw 관측과 상품 매칭은 원본 답변·제시 정의·관측 규칙에 의존한다. 결과별 Raw 영속 복사본을 추가하지 않는다.
- 세션의 `resultId`만으로 현재 FINAL을 보관하지 않는다.

### V27 보관 모델 (변경 없음)

| 컬럼 | 의미 |
|---|---|
| session_id (PK), mall_member_id | 본인 원본 세션 참조, 동일 세션 중복 보관 방지 |
| submission_revision, rule_version | 캡처한 제출 revision과 실제 판별 버전 |
| result_snapshot (JSON), captured_at | 서버 판별 응답 전체 및 최초 캡처 UTC 시각. 원본 답변 복제 아님 |
| saved_slot / saved_at | NULL 또는 슬롯 1~5와 저장 시각 |
| current_marker / current_since | NULL 또는 1과 현재 설정 시각 |
| version | JPA 낙관적 잠금 |

UNIQUE `(mall_member_id, saved_slot)` 및 CHECK 1~5, UNIQUE `(mall_member_id, current_marker)` 및 CHECK=1, 상태·시각 NULL 짝 제약을 유지한다. 슬롯은 순위가 아니다.

보관 `(session_id, mall_member_id)` → 세션 소유자 UNIQUE의 FK는 `ON DELETE RESTRICT`다. V4 답변 → 세션 FK는 `ON DELETE CASCADE`다. 따라서 원본 세션 삭제는 답변까지 제거한다. 보관 FK만으로 답변 직접 삭제를 막지는 못한다. 기존 FORM 회원과 외부 쇼핑몰 식별자 경계를 바꾸거나 새 회원 FK를 추가하지 않는다.

### V28 신규 적용 경계

`V28__add_temporary_result_expiry.sql`은 세션에 nullable `temporary_result_expires_at DATETIME(6)`과 `(mall_member_id, status, temporary_result_expires_at)` 조회 인덱스만 추가한다. DEFAULT, UPDATE, 데이터 복사·삭제는 없다.

- V28 이전 제출의 값은 NULL이며 `LEGACY_EXEMPT`로 남는다. 기존 9개를 포함한 과거 제출에 소급 적용하지 않는다.
- 새 코드의 회원 `TestSession.submit(now)`만 `submitted_at + 5일`을 기록한다. 기존 진행 중 세션이 배포 뒤 새로 제출되면 적용된다.
- 배포 중 구 코드가 제출해 NULL인 세션도 보수적으로 과거 예외로 남긴다. 나중에 일괄 backfill하지 않는다.
- 비회원의 일시적 세션 객체는 회원 ID가 NULL이며 적용 시각도 NULL이다. 객체를 DB에 저장하지 않는다.
- 저장·현재 설정과 해제는 submitted_at 및 expires_at을 수정하지 않는다.
- V28 적용 전 새 엔티티로 서버를 실행하지 않는다. 실제 적용 대상은 검증 DB `tastedna_test`인지 확인한다. 기존 V1~V27 체크섬과 사용자 데이터는 유지한다.

## 3. 구현된 회원 서비스와 API

`MemberResultService`와 `MemberResultController`를 사용한다. 모두 기존 인증 principal의 회원 ID와 소유권 경계를 유지한다.

| HTTP 경로 (`/api/v1/member/results` 기준) | 동작 |
|---|---|
| GET (root) | 현재 설정 + 저장 목록 + limit=5 |
| GET /current | 현재 항목 또는 없음 |
| GET /{id} | 본인 완료 세션의 관리 상태 |
| POST /{id}/save | 저장, 중복 요청은 멱등, 6번째는 409 RESULT_STORAGE_FULL |
| POST /{id}/unsave | 저장만 해제, 현재 설정·원본 유지 |
| POST /{id}/current | FINAL 검증 뒤 현재 지정/변경 |
| POST /current/clear | 현재만 해제, 저장·원본 유지 |

쓰기 순서: ACTIVE 회원 `findByMemberIdForUpdate` → 본인 SUBMITTED 세션 잠금 → 보관 상태 처리. 보관 행이 없는 경우에도 같은 회원 잠금으로 슬롯 배정·현재 변경을 직렬화한다. 현재 변경 시 대상 FINAL 검사를 **기존 현재 해제보다 먼저** 수행한다. 기존 marker 해제 후 flush하고 새 marker를 설정하며 같은 트랜잭션으로 묶는다. DB UNIQUE/CHECK와 `@Version`도 유지한다.

최초 보호 요청에서 서버가 `SimpleInitialCandidateService.Response`를 캡처한다. 클라이언트 RESULT JSON을 받지 않는다. sessionId/definitionId/submission revision/ruleVersion을 검사하며 보관 재조회는 스냅샷을 우선한다. 최초 스냅샷을 재저장·현재 전환으로 덮어쓰지 않는다. 과거 제출의 최초 캡처는 캡처 시점 규칙의 결과이지 미기록 과거 판별을 복원한 것이 아니다.

현재 구현은 마지막 보호를 해제해도 무보호 보관 행과 스냅샷을 남긴다. 저장 수에는 포함하지 않는다. 실제 정리가 미활성화되어 있으므로 만료 판별 후에도 이력/리포트가 조회될 수 있고 명시적으로 다시 보호할 수 있다. 이것을 5일 뒤 실제 소멸이 구현된 것으로 해석하면 안 된다.

## 4. 12C 만료 판별 및 경합 경계

`MemberResultExpiryService`는 내부 Java 서비스이며 새 HTTP API나 스케줄러는 없다.

- `review(member, sessionId)`: 회원 잠금 → 본인 세션 잠금 → 보관 행 잠금 조회 → UTC 기준 판별.
- `findExpired(member, limit)`: 같은 회원 잠금 → `findTemporaryExpiryReviewIds`로 제출/적용 시각/만료/보호 제외 조회 → 각 세션·보관 행을 잠금 조회하여 다시 판별 → EXPIRED만 반환. limit 1~100은 검토 조회 크기이며 정책상 보관 한도가 아니다.
- 조회된 ID 목록은 **삭제 허가가 아니다**. 트랜잭션 밖에서 재사용하여 삭제하면 안 된다.
- 회원 관리 쓰기와 같은 회원 잠금을 사용한다. 후보 목록과 재검사 사이에 보호 상태가 달라진 것으로 보이는 경우도 재검사에서 제외한다. 실제 MySQL 동시 트랜잭션 경쟁은 별도 검증이 필요하다.

| 상태 | 조건 |
|---|---|
| NOT_SUBMITTED | SUBMITTED 아님 또는 submittedAt 없음; IN_PROGRESS 제외 |
| PROTECTED | saved_slot 또는 current_marker 존재. 과거 미확정 현재도 보호 |
| LEGACY_EXEMPT | 무보호 완료지만 expires_at NULL: 소급 만료 금지 |
| TEMPORARY | 적용 대상이며 UTC 현재 시각이 만료 시각 이전 |
| EXPIRED | 적용 대상·무보호·만료 시각 도달/경과 |

5일은 UTC 제출 시각부터 120시간이며 경계 시각부터 EXPIRED다. 판별은 적용 값이 비정상적으로 더 이르더라도 submittedAt+5일보다 짧은 보존 기간을 허용하지 않는다.

이미 5일을 넘긴 결과의 마지막 보호 해제는 EXPIRED 판별을 가능하게 하지만, 해제 요청에서 삭제·원본 변경·기간 재시작은 하지 않는다. 추가 유예 기간은 아직 승인되지 않아 임의로 도입하지 않았다.

## 5. 실제 삭제가 비활성화된 이유와 후속 안전 조건

**삭제 코드/삭제 스케줄러 없음. 화면 숨김 없음. 기존 데이터 정리 실행 없음.**

기획 기록 08의 Deep 자료 재사용 약 90일과 회원 임시 RESULT 5일은 별개다. 현재 답변을 지우면 Raw·상품 매칭·향후 Deep 재사용 근거도 함께 사라진다. Deep에서 반드시 남길 최소 자료, 보존 기간/동의, 마지막 보호 해제 후 고지·유예가 미확정이므로 원본 삭제를 활성화할 수 없다.

후속 작업은 위 정책을 먼저 확정한 뒤, 신규 적용 표식 확인 → 회원 잠금과 보호 재확인 → Deep/기타 보존 의존성 확인 → 무보호 보관 스냅샷/파생 자료 → 허용된 원본 답변·세션 순서를 검토해야 한다. 어떤 삭제 단계도 현재 구현되거나 실행되지 않았다. 과거 NULL 기록은 별도 승인 없이 정리 대상에 포함하지 않는다. 배치 주기·실패 재처리·감사 기록·계정 탈퇴 삭제는 미정이다.

## 6. 비회원 현행 계약과 폐기된 계획

**기획 기록 08 및 12A의 ‘12시간 결과 보존/회원 전환’은 12B 사용자 결정으로 폐기된 계획**이다. 현행 정책이 아니며 후속 개발 대상으로도 예약하지 않는다.

`GuestSimpleService` / `GuestSimpleController`:

- GET `/api/v1/guest/simple/questions?apparelScope=mens|womens`: 서버가 정한 남녀 Simple 10문항.
- POST `/api/v1/guest/simple/submit`: 실제 10문항 선택 형식 검증 → 메모리 SessionAnswer → 기존 Raw/FINAL/상품 매칭 공용 계산 → 결과 묶음 반환 (`Cache-Control: no-store`).
- POST는 기존 CSRF 검증을 유지한다. 비회원 공개는 이 경로에 한정되며 회원 이력·관리 API는 여전히 인증 필요.
- 세션·답변·보관 Repository 쓰기 없음. 요청 종료 후 서버 결과 보관 없음. 공용 회원 ID 없음.
- FORM은 현재 페이지의 메모리 답변/결과만 사용한다. 새로고침/브라우저 종료 후 복원, 과거 결과 목록, 계정 저장·이전, 현재 설정을 제공하지 않는다.
- 일반 CSRF/보안 HTTP 세션과 TasteDNA 완료 결과 보존은 별개다. 비회원 결과용 테이블·캐시·만료 스케줄러는 없다.

## 7. 검증 범위와 남은 작업

자동화 검증은 DB 대역을 쓰는 서비스/MVC 테스트와 Node DOM 대역 테스트를 실제 MySQL/브라우저 검증과 구분한다.

- 회원 서비스: 5슬롯·중복·6번째 거부·슬롯 재사용·현재 교체 순서·독립 해제·소유권·서버 스냅샷 보존. FINAL 미확정 저장 허용/신규 현재 거부/거부 시 무변경/과거 현재 보존.
- 기존 회원 실제 컨트롤러 제출 경로: 신규 만료 시각 기록, 보관 Repository 자동 호출 없음, 인증·소유권 경계.
- 비회원: 실제 마이그레이션의 남녀 10문항·Raw 규칙 fixture와 기존 계산 서비스로 제출/FINAL/상품 근거 확인, 잘못된 옵션·null·불완전 응답 거부, 회원 세션·답변 Repository 무호출. 보관 Repository는 비회원 계산 경로의 의존성이 아니다.
- 만료: 5일 경계, 저장만/현재만 보호, 마지막 보호 해제, 과거 NULL·진행 중 제외, 잠금 순서와 재검사. V28이 기존 행을 backfill/삭제하지 않는 구조 검증.
- FORM: 기존 결과 재조회, 저장과 이력 구분, 미확정 설정 버튼 미노출·저장 허용, 과거 현재 해제, 비회원 결과 관리 기능 미노출.
- 선택적 `MemberResultRetentionJpaTest`: `RUN_RESULT_LIFECYCLE_MYSQL_TESTS=true`에서만 실행. URL은 `tastedna_test` 고정, 자동 Flyway 비활성, **V28까지 별도 적용** 후 ddl validate. 자체 생성 행만 테스트하고 트랜잭션 롤백. UNIQUE/FK/슬롯·현재 제약, 만료 조회와 기존 이력 보존 검증. 실제 병렬 경합/롤백 검증까지 완료한 것으로 주장하지 않는다.

이번 환경에 DB_USERNAME/DB_PASSWORD가 없어 MySQL 적용·실제 제약·동시성 검증은 미실시다. 브라우저는 사용자 수동 검증 대상이다. 실제 실행 건수는 작업 완료 보고를 따른다.

남은 범위: MySQL V28 비파괴 적용 및 조회/경합 검증, Deep 최소 보존 자료와 마지막 보호 해제 정책 결정, 그 이후에만 물리 정리 및 운영 배치 설계. 기존 12D~12F 중 비회원 보존·회원 이전은 취소됐으며 다시 구현하지 않는다.
