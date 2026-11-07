# TasteDNA V1 실행·쇼핑몰 연동 가이드

현재 구현 기준: 15-T3B3. 과거 남성복 전용·Basic 중심 안내를 이 가이드로 대체한다. FORM은 같은 출처에서 실행되는 데모 쇼핑몰이며, 외부 쇼핑몰 자동 연동 제품은 아니다.

## 실행 환경과 프로필

[README 실행 예시](../README.md#실행)를 사용한다. 프로젝트 루트에서 Java 21/Gradle Wrapper로 실행하고 MySQL 8.4의 독립 테스트 DB를 준비한다.

| 설정 | 실제 의미 |
|---|---|
| DB_USERNAME / DB_PASSWORD | MySQL 접속 계정. 비밀값은 실행 프로세스 환경에만 주입 |
| SPRING_DATASOURCE_URL | 테스트는 `jdbc:mysql://localhost:3306/tastedna_test?connectionTimeZone=UTC`로 명시. 기본 application.yaml은 tastedna를 가리킴 |
| member-auth | FORM DB 회원·서버 세션·CSRF·정적 화면·공개 데모 상품 API |
| local | DEV_MEMBER_PASSWORD를 사용하는 개발용 Basic 계정. CSRF 비활성 개발 설정이므로 운영 사용 금지 |
| 프로필 미지정 | 기본 차단 정책. member-auth와 local 동시 사용은 시작 실패 |
| AUTH_COOKIE_SECURE | 기본 true. localhost HTTP 검증에서만 false |
| server.port | Spring 기본 8080. README는 실행 인자로 8081 지정 |

회원 인증 쿠키는 FORMSESSION, HttpOnly, SameSite=Lax이며 서버 세션 유휴 만료는 30분이다. 운영 전 HTTPS·프록시·세션 저장·요청 제한 정책을 별도 검토해야 한다. member-auth는 운영 준비 완료 선언이 아니다.

Flyway는 시작 시 V1~V25 중 미적용 파일을 실행한다. JPA ddl-auto는 validate다. 기존 마이그레이션을 편집하거나 개발 DB를 초기화하지 않는다. 테스트 프로필만 bootRun에 추가한다고 자동으로 테스트 DB가 선택되는 것은 아니다.

`QuestionAssetConfig`는 작업 디렉터리의 `gender/`를 `/assets/gender/**`로 제공한다(local 또는 member-auth). JAR만 복사하면 이 이미지가 포함되지 않는다. 외부 배포에는 자산 폴더 보존 또는 정적 호스팅 연결이 필요하며 과거 답변의 이미지 URL을 깨뜨리지 않아야 한다.

## 회원과 소유권 경계

FORM은 이메일·비밀번호 가입 후 별도 로그인한다. TasteDNA용 추가 계정은 없다. `Authentication.getName()`의 내부 회원 ID가 세션의 `mallMemberId`에 연결된다. 요청 본문으로 회원 ID나 정의 ID를 지정하지 않는다. 현재 FORM 회원 ID는 `form-` UUID이며 로그인 이메일을 소유권 키로 사용하지 않는다.

외부 쇼핑몰은 자신의 검증된 인증 주체를 이 경계에 연결해야 한다. 현재 member-auth의 회원 상태 필터는 FORM 회원 테이블을 조회하므로 단순히 임의 Authentication을 주입하는 것만으로 외부 회원 연동이 완료되지 않는다. 인증 어댑터·권한·현재 회원 상태 확인을 통합하되 기존 세션 소유권 검사를 유지해야 한다. 다중 쇼핑몰 테넌트 격리와 회원 ID 네임스페이스도 별도 설계 대상이다. JWT/OAuth·범용 SDK·CORS 자동 설정은 제공하지 않는다.

인증 경로는 `/api/v1/auth/csrf` GET, `/signup` POST, `/login` POST, `/logout` POST, `/me` GET이다(모두 `/api/v1/auth` 아래). 가입·로그인·로그아웃 및 인증 후 변경 요청에 CSRF가 필요하다. GET csrf의 `headerName`·`token`을 변경 요청 헤더로 보내고, 로그인·로그아웃 후 다시 발급받는다. 쿠키는 same-origin 브라우저가 관리한다. 구체적인 JSON과 오류 처리는 [인증 계약](member-auth-contract.md)과 [프런트엔드 연결](form-frontend-auth.md)을 참고한다. 해당 인증 문서의 작업 03 초기 미구현 기록은 역사적 상태이며 현재 범위는 이 가이드와 README를 기준으로 본다.

## 실제 Simple API 흐름

모든 세션 경로는 인증된 본인만 사용한다. 아래 `{id}`는 시작 응답의 sessionId다.

| 순서 | 메서드·경로 | 계약 |
|---|---|---|
| 시작 | POST `/api/v1/sessions/simple-image?apparelScope=mens` | 여성복은 womens. 본문 없음, CSRF 필요. 서버가 준비된 정의를 선택 |
| 진행 확인 | GET `/api/v1/sessions/active` | 진행 중 세션 없으면 204 |
| 세션 | GET `/api/v1/sessions/{id}` | SessionResponse |
| 질문 | GET `/api/v1/sessions/{id}/questions` | QuestionListResponse; 실제 서버 정의를 렌더링 |
| 개별 질문 | GET `/api/v1/sessions/{id}/questions/{questionId}` | QuestionResponse |
| 저장·수정 | POST `/api/v1/sessions/{id}/answers` | SaveAnswerRequest → SessionResponse |
| 복구 | GET `/api/v1/sessions/{id}/answers` | session과 answers 스냅샷 |
| 재개 | POST `/api/v1/sessions/{id}/resume` | 본문 없음 → SessionResponse |
| 제출 | POST `/api/v1/sessions/{id}/submit` | SubmitSimpleRequest → SessionResponse |
| 기록 | GET `/api/v1/sessions/history` | SessionHistoryResponse 배열; 본인 Simple 기록, 최신순 |
| 리포트 | GET `/api/v1/sessions/{id}/preference-summary` | 제출 완료 직접 선호 요약 |
| 초기 후보 | GET `/api/v1/sessions/{id}/initial-candidates` | 후보·근거와 DEFERRED 최종 판별 상태 |
| 상품 | GET `/api/v1/sessions/{id}/matched-demo-products` | 제출 완료 세션의 등록 속성 일치 상품 |

SessionResponse: `sessionId, mode, apparelScope, status, revision, definitionId, leaseUntil`.
SessionHistoryResponse: `sessionId, mode, apparelScope, status, startedAt, submittedAt`.
회원당 진행 중 세션 보호를 유지한다. 새 세션을 강제 생성하거나 과거 definitionId를 새 정의로 바꾸지 않는다. 불완전한 정의는 422로 거부한다. 기존 POST `/sessions`도 있지만 현재 이미지 Simple 진입에는 위 전용 경로를 사용한다.

저장 예시(현재 세션 revision이 0일 때의 Q01):

```json
{"questionId":"Q01","expectedRevision":0,"answerPayload":{"selectedOptionIds":["A","B"]}}
```

매 저장 응답의 revision을 다음 요청에 반영한다. 제출 본문은 `{"expectedRevision":10}`처럼 **실제 마지막 revision**을 사용하며 10으로 고정하지 않는다. 수정하면 revision이 더 커질 수 있다. 오래된 revision의 요청을 무조건 재시도하지 말고 현재 저장값을 재조회한다.

단일 답변은 `{"selectedOptionId":"A"}`, 비교형은 다음 형식이다. 예시는 구조 설명이며 해당 문항의 실제 옵션·최소/최대·조건을 조회해 적용한다.

```json
{"selections":[{"comparisonId":"C01","selectedOptionId":"A"},{"comparisonId":"C04","selectedOptionIds":["B","A"]}]}
```

순위형 배열 순서는 순위다. 일반 복수선택 순서는 선호 순위가 아니다. NONE은 정의된 문항에서 이미지와 배타적이다. 일반 미선택과 단일 비교 대안을 비선호로 해석하지 않는다. 남성복 Q09 C02는 현재 단일 비교이며 여성복 Q08 C04/C05·Q09 C02는 순위형이다. [순위형 계약](ranked-comparison-contract.md)을 함께 확인한다.

## Raw와 결과의 경계

Raw는 저장된 최신 답변·정의·옵션 규칙으로 서버 내부에서 수집한다. 개발 조회 `GET /api/v1/sessions/{id}/raw-observations`는 **dev 프로필 전용**이며 member-auth 보안 정책에서는 공개하지 않는다. 연동을 위해 임의로 dev를 운영에 활성화하지 않는다. 외부 공개가 필요하면 별도 접근 제어·공개 DTO 계약을 정해야 한다.

현재 사용자 리포트는 `preference-summary`를 사용한다. 응답은 `session, status, title, description, comprehensiveResult, evidence`; 상태는 DIRECT_PREFERENCE_SUMMARY, comprehensiveResult는 false다. evidence에는 questionId·comparisonId·prompt·meaning·answerRevision·selections·alternatives 및 순위형 notPreferred가 있다. 선택에는 optionId·label·images와 해당하는 경우 rank가 포함된다.

Q02 단일 선택은 그 스타일, 복수는 동등한 모든 직접 선호로 표시한다. initial-candidates는 근거 질문·옵션·Raw 유형·revision을 추적하며 `finalResult=false`, `decision.status=DEFERRED`를 유지한다. 후보가 없어도 결과를 채우지 않는다. local 전용 실험 API `/simple-result`는 이미지 Simple의 운영 계약이 아니다. [후보 API](simple-initial-candidates-api.md), [기존 승인 부족 조건](simple-result-finalization-gaps.md)을 재사용한다.

리포트는 별도 최종 RESULT 저장 테이블이 아니라 저장 답변에서 재구성한다. 화면은 상세 선택 기록을 접어서 제공하고 상품 최초 4개·더 보기를 지원한다.

## 상품 시스템 연결

[상품 매칭 계약](demo-product-matching-api.md)을 따른다. 공개 FORM 목록은 GET `/api/v1/demo/products`이며 `id, name, category, price, imageUrl, description, demo`를 반환한다. 관리용 상품 등록 API는 없다.

외부 상품 공급 시 기존 DemoProduct/JPA 구조에서 `productId, name, category, price, imageUrl, description, displayOrder, styleCodes, apparelScope, colorCodes`를 준비한다. productId는 기존 쇼핑몰 상품과 대응하는 안정적인 키, imageUrl은 표시용, price는 현재 정수 가격 모델이다. 통화·옵션별 재고·판매 상태·가격 동기화는 자동 제공하지 않는다. 자체 상품 엔티티/Repository 연동 또는 명시적 동기화 구현이 필요하다.

apparelScope는 MENS/WOMENS/UNISEX 또는 미분류(null), styleCodes는 승인 Q02 R01~R12 집합, colorCodes는 승인 Q01 A~O 집합이다. category는 상품 분류로 styleCodes와 다르다. 없는 속성은 빈 집합으로 유지한다. 모델 외형이나 유사 코드로 속성을 추론하지 않는다. [범위 근거](demo-catalog-apparel-scope.md), [색상 코드·등록 근거](demo-catalog-colors.md)를 참고한다.

범위 적합 + Q02 스타일 일치가 추천 자격이다. Q01 색상 일치 상품이 먼저이며 각 그룹 내부는 상품 ID순이다. 색상 불일치·미등록도 유지한다. Q01이 없으면 기존 ID순이다. 점수·일치율·색상 일치 개수 순위는 없다. RESULT 유형은 검색 조건이 아니다. 현재 데모 100개 등록과 실제 판매 카탈로그 완성은 구분한다.

## 외부 재현 시 남아 있는 환경 의존성

- 기본 DB URL은 localhost/tastedna: 테스트와 배포마다 명시적으로 재지정해야 한다.
- gender 파일 경로는 작업 디렉터리 기준이며 외부 자산 배포는 수동 연결이 필요하다.
- Q01·Q02 승인 어휘는 서비스에 명시되어 있고 초기 후보의 승인 매핑 조회에는 기존 정의 ID가 남아 있다. 임의 질문 어휘를 자동 수용하는 범용 엔진이 아니다.
- FORM 인증과 상품 목록·회원 화면은 데모 구현이다. 외부 인증/카탈로그 연동과 운영 HTTPS·분산 환경은 별도 작업이다.
- MySQL 통합 테스트는 localhost:3306/tastedna_test 및 테스트 계정명 coql을 의도적으로 제한한다. 외부 개발자는 별도 테스트 DB와 해당 제한 계정을 준비해야 한다. 운영 DB로 안전장치를 우회하지 않는다.

## 검증 재현

평소에는 변경 영역만 실행한다. [README](../README.md)의 Java·Node 예시를 사용한다. 실제 MySQL 통합 검증은 독립 DB와 coql 테스트 계정을 준비하고 DB_USERNAME=coql, DB_PASSWORD를 프로세스 환경에 주입한 후 실행한다.

```powershell
$env:RUN_MEMBER_MYSQL_TESTS='true'
try {
    .\gradlew.bat test --tests 'com.tastedna.member.MemberAuthMysqlIntegrationTest' --rerun-tasks
} finally {
    Remove-Item Env:RUN_MEMBER_MYSQL_TESTS -ErrorAction SilentlyContinue
}
```

비밀번호 입력·정리는 README의 숨김 입력 방식을 사용한다. 이 테스트는 Flyway 적용과 검증용 회원·세션 추가를 수행할 수 있다. 기존 데이터 삭제·초기화를 하지 않는다. BUILD SUCCESSFUL과 함께 XML의 tests/failures/errors/skipped를 확인한다. 보고서는 `build/test-results/test`와 `build/reports/tests/test/index.html`에 생성된다. 이 문서 작업에서는 DB나 브라우저를 재실행하지 않았다. 기능별 검증 수준과 미완성 항목은 README에 요약되어 있다.
