# TasteDNA 작업 03 — FORM 회원 인증 백엔드 인계

작성: 2026-11-04. 기준/현재 HEAD: `cb027a92b1dd34a61c8f7776b2c133ffae889de8`.
시작 브랜치는 master, 작업 트리는 깨끗했다. 작업 02 순위형 기능은 유지한다.
이 문서는 백엔드 구현 및 검증 범위를 설명하며 운영 준비 완료를 의미하지 않는다.

## 구현 및 파일

신규 의존성 없이 Spring Security, 서버 HttpSession, JPA/MySQL을 사용한다.
JWT, OAuth, 회원 자동 이관, UI 변경, RESULT 변경은 없다.

수정 3개:

- `src/main/java/com/tastedna/config/SecurityConfig.java`: local/기본 차단과 member-auth 프로필 분리.
- `src/main/java/com/tastedna/session/api/ImageAssessmentController.java`: member-auth에서도 기존 시작 API 사용.
- `src/main/java/com/tastedna/scoring/api/PreferenceSummaryController.java`: member-auth에서도 기존 답변/요약 API 사용.

추가 15개:

- `src/main/java/com/tastedna/config/MemberSecurityConfig.java`: DAO 인증, CSRF, 세션, 접근 제어, JSON 오류와 로그아웃.
- `src/main/java/com/tastedna/member/api/AuthCredentials.java`: 입력 정규화·검증, toString 민감값 제거.
- `src/main/java/com/tastedna/member/api/MemberAuthController.java`: 인증 API 및 컨텍스트 저장.
- `src/main/java/com/tastedna/member/application/MemberAuthService.java`: 가입·최소 회원 정보 조회.
- `src/main/java/com/tastedna/member/application/AuthAttemptLimiter.java`: 단일 서버 IP별 시도 제한.
- `src/main/java/com/tastedna/member/domain/FormMember.java`: JPA 회원 엔티티.
- `src/main/java/com/tastedna/member/persistence/FormMemberRepository.java`: JPA 저장소.
- `src/main/resources/application-member-auth.yaml`: 세션 쿠키·만료 설정.
- `src/main/resources/db/migration/V16__create_form_member.sql`: 비파괴 회원 테이블 추가.
- `src/test/java/com/tastedna/member/MemberAuthHttpTest.java`: 실제 필터/서비스, 격리 저장소 기반 HTTP 검증.
- `src/test/java/com/tastedna/member/MemberCookieHttpTest.java`: 실제 내장 서버 쿠키 검증.
- `src/test/java/com/tastedna/member/LocalAuthenticationRegressionTest.java`: 개발 계정 3개 유지.
- `src/test/java/com/tastedna/member/DefaultSecurityRegressionTest.java`: 기본 차단/혼합 프로필 차단.
- `src/test/java/com/tastedna/member/application/AuthAttemptLimiterTest.java`: 제한 횟수/만료 검증.
- `docs/member-auth-contract.md`: 이 문서.

## 데이터와 식별 정책

현재 저장소의 최신 버전 V15 뒤에 V16을 추가했다. 기존 적용 파일은 수정하지 않았다.
실제 DB의 Flyway 이력은 접속 정보가 없어 확인하지 못했고 V16을 실행하지 않았다.

`td_form_member`:

| 컬럼 | 형식 | 의미 |
| --- | --- | --- |
| member_id | VARCHAR(41), PK | 서버 생성 `form-` + UUID |
| email | VARCHAR(254), ascii_bin, UNIQUE | 앞뒤 공백 제거 후 Locale.ROOT 소문자 |
| password_hash | VARCHAR(100) | BCrypt 비용 12의 해시 |
| created_at | DATETIME(6) | UTC 가입 시각 |
| status | VARCHAR(16), CHECK | ACTIVE 또는 DISABLED, 가입 시 ACTIVE |
| role | VARCHAR(16), CHECK | MEMBER만 허용 |

이메일 전체를 대소문자 구분 없이 취급한다. ASCII 이메일만 지원하고 플러스 주소나 점을
임의로 제거하지 않는다. 중복 사전 조회와 DB 고유 제약을 함께 사용한다.
이름·전화번호·주소·생년월일·비밀번호 확인값은 수집하지 않는다.

비밀번호는 **최소 12문자, 최대 72 UTF-8 바이트**다. 제어문자를 거부하며 공백 제거나
대소문자 변환을 하지 않는다. 문자 종류 혼합을 강제하지 않는다. 72바이트를 넘으면
BCrypt에서 잘리는 대신 입력 오류로 거부한다. 비밀번호/해시/쿠키/토큰을 로그에 추가하지 않았다.
운영에서도 HTTP 본문·Cookie·Authorization 헤더를 기록하는 진단 로깅을 켜지 않아야 한다.

로그인 조회 키는 이메일이지만 인증 주체의 `Authentication.getName()`은 내부 member_id다.
기존 컨트롤러가 그 값을 기존 서비스/Repository의 mallMemberId 조건에 그대로 전달한다.
mall_member_id는 VARCHAR(128)이므로 새 ID가 들어가며 기존 ID 3개와 충돌하지 않는다.
기존 세션에 새 회원 FK를 추가하지 않아 외부 쇼핑몰 식별자를 받는 TasteDNA 계층을 유지한다.
인증 주체를 생성하는 경계만 교체할 수 있고 요청 본문의 회원 ID는 소유권에 사용하지 않는다.

## 실행 프로필

| 프로필 | 동작 |
| --- | --- |
| local | 기존 개발용 Basic 및 3개 계정 유지. 기존 UI용이며 CSRF 비활성인 개발 설정 |
| member-auth | 신규 DB 회원, 서버 세션, CSRF. Basic/폼 로그인 비활성 |
| 둘 다 아님 | 기존 기본 차단 유지. 헬스 체크 외 허용하지 않음 |
| local + member-auth | 시작 실패로 혼합 차단 |

member-auth는 백엔드 검증용으로 명시적으로 활성화해야 한다. 이번에 기존 UI를 수정하지 않았으므로
이 프로필에서 기존 Basic 로그인 화면을 사용할 수 없다. 인증 및 필요한 세션 API만 허용하고
전시 UI, 실험용 Raw/최종 분류 API 등은 열지 않는다. 향후 UI 연결 때 정적 자산 공개 범위도 검토한다.

개발 계정 dev-member-001, dev-member-002, dev-image-demo는 local에만 메모리 계정으로 남는다.
회원 테이블에 복사하지 않았으며 기존 세션·답변·관측·결과도 수정하지 않았다.
기존 DEV_MEMBER_PASSWORD와 local 시작 방식은 유지된다. member-auth에는 해당 변수가 필요 없다.

별도 로컬 검증 예시(기존 개발 서버와 다른 포트, 자격 증명은 환경변수로 주입):

```powershell
$env:JAVA_HOME='C:/Users/User/.jdks/temurin-21.0.11'
$env:SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/tastedna_test?connectionTimeZone=UTC'
# DB_USERNAME/DB_PASSWORD는 허가된 테스트 DB 계정으로 별도 주입한다.
$env:AUTH_COOKIE_SECURE='false' # localhost HTTP 검증에서만 사용
.\gradlew.bat --offline bootRun '--args=--spring.profiles.active=member-auth --server.address=127.0.0.1 --server.port=8081'
```

위 실행은 실제로 Flyway를 적용할 수 있으므로 별도 테스트 스키마와 권한을 먼저 확인한다.
test YAML은 src/test/resources에만 있으므로 일반 bootRun에서 test 프로필만 추가해
DB가 자동으로 tastedna_test로 바뀐다고 가정하면 안 된다. 운영에서는 HTTPS 및 Secure=true를 사용한다.

## 세션·쿠키·CSRF

- FORMSESSION: HttpOnly, Secure 기본 true, SameSite=Lax, Path=/, Domain 미지정.
- 영속 로그인 쿠키가 아닌 세션 쿠키. 서버 유휴 만료 30분, URL 세션 추적 비활성(COOKIE만).
- 로그인 시 ChangeSessionIdAuthenticationStrategy로 세션 ID를 바꾼다.
- Spring Security 컨텍스트를 HttpSessionSecurityContextRepository에 명시적으로 저장한다.
- 로그인 시 기존 CSRF 토큰을 폐기한다. 로그아웃 시 인증 컨텍스트·서버 세션을 폐기하고 쿠키를 만료한다.
- CSRF 토큰은 서버 세션에 보관하고 GET 응답으로 전달한다. 기본 XOR 마스킹을 유지한다.
- 가입·로그인·로그아웃을 포함한 모든 보호 POST에는 같은 세션의 X-CSRF-TOKEN이 필요하다.
- CORS 허용을 추가하지 않았다. 같은 출처에서 credentials: 'same-origin'으로 호출한다.
- 인증/CSRF 응답은 Spring Security 기본 Cache-Control: no-store를 사용한다.

## API 계약

회원 응답은 `{ "memberId": "form-UUID", "email": "member@example.com" }`다.
해시·권한·상태·가입 시각 등 내부 정보는 반환하지 않는다.

| 메서드/경로 | 요청 | 성공 | 인증 조건 |
| --- | --- | --- | --- |
| GET /api/v1/auth/csrf | 없음 | 200 `{headerName:"X-CSRF-TOKEN", token:"..."}` | 익명 허용, CSRF용 세션 생성 가능 |
| POST /api/v1/auth/signup | `{email,password}` | 201 회원 응답 | CSRF 필요, 자동 로그인 없음 |
| POST /api/v1/auth/login | `{email,password}` | 200 회원 응답 | CSRF 필요, 인증 세션 생성/교체 |
| POST /api/v1/auth/logout | 본문 불필요 | 204 | CSRF 필요, Spring Security LogoutFilter가 처리 |
| GET /api/v1/auth/me | 없음 | 200 회원 응답 | 인증 필요 |

JSON 오류는 기존 공통 형식 `{ "code": "...", "message": "..." }`를 따른다.

| 상태 | code | 의미 |
| --- | --- | --- |
| 400 | VALIDATION_FAILED / INVALID_REQUEST_BODY | 입력 또는 JSON 오류 |
| 401 | INVALID_CREDENTIALS | 잘못된 비밀번호·미등록 이메일·비활성 계정의 공통 로그인 오류 |
| 401 | AUTHENTICATION_REQUIRED | 익명/만료 세션의 보호 자원 조회 |
| 403 | CSRF_INVALID | 토큰 누락·불일치, 인증 여부보다 먼저 발생할 수 있음 |
| 403 | ACCESS_DENIED | 권한 부족 또는 허용하지 않은 경로 |
| 404 | SESSION_NOT_FOUND | 세션 없음 또는 다른 회원 소유. 둘을 구별하지 않음 |
| 409 | EMAIL_ALREADY_REGISTERED | 정규화된 이메일 중복 가입 |
| 429 | AUTH_RATE_LIMITED | 가입/로그인 요청 제한 |

로그아웃은 유효 CSRF를 가진 익명 세션에도 204를 반환하는 멱등 동작이다.
가입 중복 오류는 사용자 요구에 따라 제공하므로 가입 API를 통한 이메일 추정 가능성은 남는다.
로그인은 이메일 존재 여부를 다른 오류로 노출하지 않는다.

### 프론트엔드 요청 예시

아래는 연동 예시이며 프로젝트의 UI 파일에 적용하지 않았다.

```javascript
async function getCsrf() {
  const response = await fetch('/api/v1/auth/csrf', {credentials: 'same-origin', cache: 'no-store'});
  if (!response.ok) throw new Error('CSRF_FETCH_FAILED');
  return response.json();
}
async function postJson(path, body, csrf) {
  return fetch(path, {
    method: 'POST', credentials: 'same-origin',
    headers: {'Content-Type': 'application/json', [csrf.headerName]: csrf.token},
    body: body === undefined ? undefined : JSON.stringify(body)
  });
}
let csrf = await getCsrf();
let response = await postJson('/api/v1/auth/signup', {email, password}, csrf);
// 201을 확인한다. 이 시점에는 아직 로그인되어 있지 않다.
response = await postJson('/api/v1/auth/login', {email, password}, csrf);
if (!response.ok) throw new Error((await response.json()).code);
csrf = await getCsrf(); // 로그인으로 폐기된 이전 토큰을 재사용하지 않는다.
const me = await fetch('/api/v1/auth/me', {credentials: 'same-origin'});
// 답변 저장/제출 등도 postJson(..., csrf)로 호출한다.
response = await postJson('/api/v1/auth/logout', undefined, csrf);
if (response.status === 204) csrf = await getCsrf();
```

세션 쿠키는 브라우저가 관리하므로 JavaScript에서 읽거나 localStorage에 저장하지 않는다.
Basic Authorization 헤더를 보내지 않는다. 비밀번호·CSRF 값은 로그/영구 저장소에 남기지 않는다.
401이면 로그인 상태를 해제하고, 403 CSRF_INVALID이면 토큰을 갱신하되 변경 요청을 무조건 자동 재시도하지 않는다.
다른 탭에서 로그인/로그아웃한 경우에도 이전 토큰이 무효화될 수 있다.

## 작업 03 최초 검증 결과 및 한계 (03-1 후속 결과는 아래 참고)

Java 21 관련 테스트 106개 통과(실패/오류/건너뜀 0), 그중 새 인증 테스트 13개다.
Repository 대역을 사용한 HTTP 테스트와 실제 내장 서버 쿠키 테스트를 구분한다.
기존 Node 프론트엔드/순위형 테스트도 10개 통과했다. git diff --check 통과.

- 정상 가입, 이메일 정규화/중복, 동시 가입 고유 제약 오류 매핑, 입력 오류, BCrypt 저장.
- 정상 로그인, 잘못된 비밀번호/미등록/비활성 계정 공통 오류, me, 로그아웃 및 세션 무효화.
- 로그인 전후 세션 ID 교체, CSRF 토큰 교체, 모든 인증 POST의 무토큰 거부.
- 인증 후 데이터 변경의 무토큰 거부, 미인증/권한 부족/실험 API 거부.
- 다른 회원의 세션/질문/답변/요약 조회 및 답변 저장/제출 거부.
- 가입 회원 내부 ID로 기존 남성복 10문항 시작→저장→조회→제출→직접 선호 요약.
- local 개발 계정 3개의 기존 이름 전달, 기본 환경 차단, 프로필 혼합 거부.
- 실제 내장 서버의 HttpOnly/Secure/SameSite/Path/Domain 및 no-store 헤더.
- IP별 제한 및 만료, 기존 남성복·순위형 회귀 테스트.

```powershell
$env:JAVA_HOME='C:/Users/User/.jdks/temurin-21.0.11'
.\gradlew.bat --offline test --tests 'com.tastedna.member.*' --tests '*Ranked*Test' --tests '*ImageDefinitionTest' --tests '*ImageSessionStartTest' --tests '*PreferenceSummaryServiceTest' --tests '*TestSessionServiceTest' --tests '*TestSessionTest' --tests '*SingleChoice*Test' --tests '*MultiChoiceExclusiveOptionTest' --tests '*DirectObservationCollectorTest'
node --test dev/verification/assessment-state.test.mjs dev/verification/ranked-rendering.test.mjs
```

실제 MySQL/Flyway 적용·DB 고유 제약 경쟁·JPA 스키마 검증은 미실행이다.
DB_USERNAME/DB_PASSWORD가 실행 환경에 없으며 개발 DB에 접속하거나 데이터를 변경하지 않았다.
저장소 대역에서 고유 제약 예외를 재현한 테스트를 실제 MySQL 동시성 검증으로 간주하지 않는다.
실제 브라우저 로그인 흐름·HTTPS 프록시 환경도 미검증이다. 전체 프로젝트 통합 테스트를 돌린 것은 아니다.

## 남은 보안/운영 과제

- 실제 테스트 DB에서 V16/JPA 검증 및 HTTP 인증·회원 간 격리 재검증, HTTPS 배포 검증.
- 이메일 소유 확인, 비밀번호 재설정·변경, 계정 관리 기능은 미구현.
- DISABLED는 신규 로그인과 me에서 거부한다. 이미 로그인한 다른 세션의 즉시 강제 만료는 미구현이며
  계정 상태 변경을 운영 기능으로 노출하기 전에 모든 세션 폐기 정책이 필요하다.
- 요청 제한은 원격 IP당 가입/로그인 합계 10분에 30회, 최대 10,000 IP의 메모리 상태다.
  성공도 횟수에 포함하며 재시작 시 초기화된다. 대규모 분산 공격/다중 서버/봇 대응을 대신하지 않는다.
  X-Forwarded-For를 직접 신뢰하지 않으므로 프록시 뒤에서는 공용 IP 제한이 발생할 수 있다.
  신뢰 프록시 및 배포 계층의 제한 정책을 정해야 한다. IP 상태는 영구 저장하지 않는다.
- HttpSession은 단일 서버 메모리다. 다중 서버 공유 세션, 절대 만료시간, 모든 기기 로그아웃은 미구현.
- 운영 로그/모니터링의 비밀값 제외, 비밀번호 유출 목록 검사, 가입 API 이메일 추정 완화는 후속 검토 대상.
- 외부 쇼핑몰 인증 연동, 전체 쇼핑몰 UI, 여성복/Deep/RESULT/상품 추천은 이번 범위 밖이다.

작업 03 최초 종료: master, 기존 3개 수정 + 신규 15개 파일(총 18개), 미커밋.
커밋·푸시·DB 적용은 수행하지 않았다. 프론트엔드 및 V1~V15는 변경하지 않았다.

구현 참고: [Spring Security 세션 인증 저장](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/session-management.html),
[CSRF 로그인/로그아웃 토큰 갱신](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
실제 컴파일·검증에는 프로젝트에 설치된 Spring Security 7.1.1을 사용했다.

## 작업 03-1 후속 — 실제 MySQL 통합 검증 완료

검증 일시: 2026-11-04 16:10 KST. 이 절이 위의 최초 MySQL 미검증 상태를 대체한다.

### 대상과 적용 결과

- 서버: localhost:3306, MySQL 8.4.10.
- DB: tastedna_test, 인증 계정: coql@localhost.
- 읽기 전용 사전 조회로 DATABASE()와 권한을 확인했다. 계정은 USAGE 외에 tastedna_test.* 권한만 있었다.
- 기존 DB/계정을 재생성하거나 초기화하지 않았다. 기존 tastedna 개발 DB에는 접속·변경하지 않았다.
- 기존 Flyway 버전은 V7이었다. 체크섬 검증 후 V8~V16의 미적용 9개만 정상 적용했다.
- 최종 V1~V16 이력의 success=1을 재조회했고 Flyway validate도 통과했다.
- Hibernate ddl-auto=validate 및 EntityManagerFactory 기동에 성공했다. 회원 엔티티를 포함한 실제 JPA 매핑 검증이다.
- 접속 비밀번호는 숨김 입력으로 받아 해당 프로세스 환경변수에서만 사용했다. 소스·설정·보고서에 저장하지 않았다.

### 추가 테스트와 실제 DB 검증

추가 파일: `src/test/java/com/tastedna/member/MemberAuthMysqlIntegrationTest.java`.

실제 Spring Security 필터·MockMvc HTTP 요청·서비스·JPA Repository·MySQL을 연결했다.
이 클래스에는 Repository 대역이 없으며, 테스트 메서드 전체를 롤백 트랜잭션으로 감싸지 않아
서비스의 실제 커밋과 재조회까지 확인한다. 브라우저 UI 자동화 테스트는 아니다.

1. V16 도달, 적용 이력 16개, Flyway validate 및 실제 JPA 조회 통과.
2. 서로 다른 실제 회원 가입, 정규화 이메일 중복 거부, BCrypt 저장·검증, 잘못된 비밀번호 거부,
   로그인·세션 ID 교체·me·로그아웃·세션 무효화 통과.
3. 두 스레드의 동일 이메일 JPA INSERT 경쟁에서 한 건만 성공하고 다른 건은 DB 고유 제약으로 거부됨.
4. 회원 B의 내부 ID로 기존 남성복 Simple 세션 생성→10문항 저장→DB 재조회→제출→직접 선호 요약 통과.
5. 회원 A의 B 세션·질문·답변·결과 조회 및 답변 변경·제출 요청 거부 확인.
6. 마이그레이션 직전의 기존 테스트 DB 세션·답변을 메모리에 스냅샷으로 보관하고 테스트 종료 후
   원본 저장값이 그대로인지 비교했다. V12가 재정의하는 생성 컬럼 active_member_id만 비교에서 제외했다.

현재 테스트 실행으로 생성된 검증용 데이터는 회원 3개, 세션 1개, 답변 10개다.
회원 이메일은 `mysql-` 접두어·임의 UUID·example.test 도메인으로 구분된다.
삭제나 초기화를 하지 않고 검증 기록으로 남겼다. 재실행도 새로운 UUID를 사용하므로 데이터가 추가된다.

### 실행 안전장치와 재현

- RUN_MEMBER_MYSQL_TESTS=true를 명시하지 않으면 새 통합 테스트는 실행되지 않는다.
- 테스트 프로퍼티에서 JDBC URL을 localhost:3306/tastedna_test로 고정하고 coql 계정을 요구한다.
- Flyway clean은 비활성화한다. 마이그레이션 전에 실제 catalog와 인증 계정을 다시 확인한다.
- JPA 초기화 전 확인/기존 데이터 스냅샷에만 테스트용 JDBC를 사용한다. 이 시점에는 JPA를 사용할 수 없다.
  회원 업무 로직과 검증의 가입·조회·저장은 기존 JPA를 사용한다.
- MockMvc 요청 덤프와 Hibernate 바인딩 로그를 비활성화해 인증 값 노출을 막는다.

별도 PowerShell에서 환경변수를 준비한 후 실행한다. 비밀번호를 명령 문자열에 직접 넣지 않는다.

```powershell
$env:JAVA_HOME='C:/Users/User/.jdks/temurin-21.0.11'
$env:DB_USERNAME='coql'
$env:RUN_MEMBER_MYSQL_TESTS='true'
$testSecret=Read-Host 'Test DB password' -AsSecureString
try {
    $env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', $testSecret).Password
    .\gradlew.bat --offline --no-daemon test --rerun-tasks --tests '*MemberAuthMysqlIntegrationTest'
} finally {
    if (Test-Path Env:DB_PASSWORD) { Remove-Item Env:DB_PASSWORD }
    Remove-Item Env:RUN_MEMBER_MYSQL_TESTS
}
```

이번 실행은 새 MySQL 테스트 3개와 기존 인증·남성복·순위형 Java 테스트를 함께 실행했다.
XML 보고서 20개 기준 **109개 통과, 실패/오류/건너뜀 0**.
기존 Node 상태/렌더링 회귀 테스트도 **10개 통과**했다.

### 발견 사항과 남은 한계

- 인증 제품 코드의 결함은 이번 검증에서 발견되지 않아 런타임 코드 변경은 없다.
- 별도 최종 확인 SQL에서 회원 ID와 세션 소유자 ID를 직접 JOIN할 때 MySQL 1267 오류가 발생했다.
  td_form_member.member_id는 utf8mb4_0900_ai_ci, td_test_session.mall_member_id는 utf8mb4_unicode_ci다.
  확인 쿼리에 `CAST(... AS BINARY)` 비교를 명시해 회원/세션/답변 개수 재확인에 성공했다.
  현재 인증 서비스는 이 테이블 간 JOIN을 사용하지 않아 기능 테스트는 모두 통과했다.
  향후 직접 JOIN/FK를 추가할 때 collation 정합성을 검토해야 한다. 적용된 V16이나 기존 스키마를 바꾸지 않았다.
- Gradle은 BUILD SUCCESSFUL이며 XML도 전부 성공이었다. 실행 래퍼는 이미 제거한 환경변수의 정리 때문에
  종료 상태 1을 반환했다. 이는 테스트 실패가 아니며 이후 읽기 전용 확인 명령은 정상 종료했다.
- 실제 브라우저·HTTPS 프록시·계정 비활성화 후 기존 세션 강제 폐기 등 앞서 기재한 후속 과제는 남아 있다.
- 프론트엔드, 기존 질문/답변 계약, Flyway V1~V15는 수정하지 않았다.

이번 작업의 파일 변경은 새 MySQL 테스트 1개와 이 문서 갱신뿐이다.
최종 master HEAD는 기준 커밋 그대로이며, 누적 수정 3개·신규 16개가 미커밋 상태다.
커밋·푸시는 수행하지 않았다.
