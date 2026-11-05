# 작업 04 — FORM 회원 인증 프론트엔드 연결

기준 HEAD: `cf4cfb6b60eb65444e89f7f9832b1685ec190b5d`. 시작 작업 트리는 깨끗했다.

## 사용자 흐름

1. FORM 컬렉션은 로그인 없이 열람한다. 전시 상품은 구매/추천 기능이 아닌 데모 표시를 유지한다.
2. 회원 메뉴 또는 TasteDNA → 남성 선택에서 FORM 로그인으로 진입한다.
3. 회원가입은 이메일·비밀번호·비밀번호 확인을 받는다. 확인값은 서버에 보내지 않는다.
   가입 성공 후 로그인 화면으로 이동하며 자동 로그인하지 않는다.
4. 명시적 로그인 후 원래 요청한 테스트로 복귀한다. 저장된 세션 링크가 있으면 해당 세션을,
   없으면 진행 중 세션을 조회하고, 진행 중 세션도 없을 때 남성복 Simple을 시작한다.
5. 기존 질문 조회 → 답변 저장 → 제출 → 직접 선호 요약 화면을 그대로 이용한다.
6. 새로고침 시 me로 로그인 상태를 확인한다. 세션 링크로 다시 열면 저장된 응답을 복원한다.
7. 로그아웃 시 서버 인증 세션과 화면의 회원·답변·질문 상태를 비운 뒤 컬렉션으로 돌아간다.
   보호 요청의 401(비활성 회원 포함)도 화면 데이터를 비우고 FORM 로그인으로 안내한다.

회원가입은 FORM 회원 기능이다. TasteDNA 전용 회원가입이나 별도 계정은 없다.
여성복 준비 중 표시, 질문 정의, 선택 정책, 종합 RESULT 미제공 안내는 유지했다.

## 인증과 공개 경로

- GET `/api/v1/form/config`는 `{"authMode":"member"}` 또는 `{"authMode":"local"}`만 반환한다.
  설정 조회가 실패하면 연결 오류를 표시하며 Basic 인증으로 전환하지 않는다.
- member-auth에서는 GET `/`, `/index.html`, 지정한 JS/MJS/CSS 파일, `/assets/gender/**`,
  `/api/v1/demo/products`, 위 설정 API만 추가 공개한다. 테스트 API의 인증·소유권 검사는 유지한다.
- 기존 DemoProductController와 QuestionAssetConfig를 member-auth에서도 사용한다.
  이미지는 기존 저장소의 `gender` 디렉터리에서 제공하므로 프로젝트 루트에서 실행한다.
- form-auth.mjs는 같은 출처 쿠키를 사용하며 member 모드에서 Authorization 헤더를 보내지 않는다.
  Basic 설정은 local 모드에서만 가능하다. 비밀번호·쿠키·인증 정보·CSRF는 Web Storage에 저장하지 않는다.
- POST 전에 `/api/v1/auth/csrf`의 headerName/token을 사용한다. 로그인·로그아웃 후 토큰을 비우고
  다음 변경 요청 전에 새로 조회한다. CSRF_INVALID로 거부된 요청만 토큰 갱신 후 한 번 재시도한다.
  다른 오류나 네트워크 실패는 변경 요청을 자동 재전송하지 않는다.
- 기존 HttpOnly/SameSite=Lax 쿠키 정책을 유지한다. HTTPS에서는 기본 Secure=true를 사용한다.
  로컬 HTTP 검증에만 AUTH_COOKIE_SECURE=false가 필요하다. local과 member-auth를 동시에 켜지 않는다.

## 실행 및 검증 범위

이번 작업은 개발 DB를 사용하지 않았다. 브라우저 종단 검증용 실행 서버도 없었으므로
실제 브라우저·MySQL·HTTPS 프록시를 연결한 가입부터 요약까지의 검증은 미수행이다.
서버를 실행할 때는 먼저 독립 테스트 DB 접속 정보를 환경변수로 지정하고,
SPRING_DATASOURCE_URL 및 Flyway 별도 URL을 사용하는 경우 그 URL도 tastedna_test인지 확인해야 한다.
기본 application.yaml의 tastedna에 그대로 연결해 검증하지 않는다.

자동 테스트 결과:

- Java: **110개 통과, 실패 0, MySQL 전용 3개 건너뜀**. 실제 Security/MockMvc/서비스와 Repository 대역으로
  공개 정적 파일·상품/설정 경로, 인증·CSRF·회원별 소유권, 비활성 세션 차단,
  가입 회원의 남성복 10문항 저장·제출·직접 선호 요약, local 3계정과 순위형 회귀를 확인했다.
  기존 내장 HTTP 서버의 쿠키 속성 테스트도 포함한다.
- Node: **16개 통과**. 인증 전송·토큰 갱신·제한된 재시도·local Basic 분리,
  실제 app.js 핸들러의 명시적 로그인·원래 세션 복귀·401/로그아웃 정리,
  기존 선택 상태·순위 표시·요약/다운로드 회귀를 확인했다. 화면 테스트는 DOM 대역이며 브라우저 검증이 아니다.
- 추가 화면 테스트의 최초 실행은 DOM 대역이 존재하지 않는 다음 버튼까지 반환해 실패했다.
  대역을 수정한 뒤 16개 모두 통과했다. 제품 코드 결함으로 처리하지 않았다.

```powershell
.\gradlew.bat --offline test --tests 'com.tastedna.member.*' --tests '*Ranked*Test' --tests '*ImageDefinitionTest' --tests '*ImageSessionStartTest' --tests '*PreferenceSummaryServiceTest' --tests '*TestSessionServiceTest' --tests '*TestSessionTest' --tests '*SingleChoice*Test' --tests '*MultiChoiceExclusiveOptionTest' --tests '*DirectObservationCollectorTest'
node --test dev/verification/assessment-state.test.mjs dev/verification/ranked-rendering.test.mjs dev/verification/form-auth.test.mjs dev/verification/form-flow.test.mjs
```

## 변경 파일

- src/main/java/com/tastedna/config/FormClientConfigController.java — 신규 공개 모드 조회
- src/main/java/com/tastedna/config/MemberSecurityConfig.java — FORM 읽기 경로 허용
- src/main/java/com/tastedna/config/SecurityConfig.java — local 설정/인증 모듈 읽기 허용
- src/main/java/com/tastedna/config/QuestionAssetConfig.java — member-auth 이미지 제공
- src/main/java/com/tastedna/demo/DemoProductController.java — member-auth 데모 조회
- src/main/resources/static/form-auth.mjs — 신규 인증 전송 모듈
- src/main/resources/static/app.js — FORM 회원 화면·상태·기존 테스트 진입 연결
- src/main/resources/static/index.html — FORM 제목·회원 메뉴 영역
- src/main/resources/static/shop.css — 중립 색상·회원 화면 스타일
- src/test/java/com/tastedna/member/MemberAuthHttpTest.java — 공개/보호 경계 검증
- src/test/java/com/tastedna/member/LocalAuthenticationRegressionTest.java — local 설정 검증
- dev/verification/form-auth.test.mjs — 신규 전송 테스트
- dev/verification/form-flow.test.mjs — 신규 화면 핸들러 테스트
- dev/verification/ranked-rendering.test.mjs — 초기화 분리에 맞춘 기존 테스트 대역 조정
- docs/form-frontend-auth.md — 이 문서

shop.mjs의 기존 화면과 상품 호출은 그대로 재사용했다. 개발 DB, Flyway V1~V16, 회원/세션/답변 데이터는
변경하지 않았다. 신규 의존성을 추가하지 않았고 커밋·푸시하지 않았다.

## 작업 04 최종 실제 브라우저 검증 — 2026-11-05

위 자동 테스트 당시의 브라우저 미검증 항목을 이번에 아래 범위까지 검증했다.

- MySQL84 서비스가 중지되어 최초 접속은 10061로 실패했다. 서비스를 시작한 뒤
  DATABASE()=tastedna_test, CURRENT_USER()=coql@localhost 및 tastedna_test.* 한정 권한을 확인했다.
- member-auth 서버를 127.0.0.1:18084에서 실행했다. datasource/Flyway URL 모두 tastedna_test로 지정했다.
  Flyway V1~V16은 이미 적용된 상태였으며 기존 파일·스키마를 변경하지 않았다.
- 실제 Codex 내장 브라우저에서 FORM 접속 → 회원가입 → 명시적 로그인 → TasteDNA → 남성복
  10문항 응답 저장 → 제출 → 직접 선호 요약 → 로그아웃에 성공했다.
- 로그인 후 새로고침 유지, 10개 답변의 새로고침 복원, 로그아웃 후 결과 링크의 FORM 로그인 안내,
  재로그인 후 원래 결과 복귀를 확인했다. 요약 화면도 스크린샷으로 확인했다.
- 같은 문서 내 #session 링크 이동이 화면에 반영되지 않는 문제를 발견해 app.js에 hashchange 처리를
  최소 추가했다. 실제 브라우저 재검증과 신규 회귀 테스트가 통과했다.
- 같은 실제 서버에 별도 HTTP 요청으로 HttpOnly·SameSite=Lax·세션 쿠키·no-store를 확인했다.
  로컬 HTTP 검증에만 Secure=false를 환경변수로 적용했으며 설정 파일의 HTTPS 기본값은 유지했다.
  무인증 me/진행 세션/답변/요약은 401, CSRF 없는 로그아웃은 403 CSRF_INVALID였다.
  정상 CSRF를 사용하는 가입·로그인·답변 저장·제출·로그아웃은 실제 브라우저에서 성공했다.
- 테스트 DB에 검증 회원 1개, 세션 1개, 답변 10개가 추가됐다. 읽기 전용 재조회에서 BCrypt 형식,
  SUBMITTED 상태, revision=10 및 답변 10개를 확인했다. 기존 데이터는 수정·삭제하지 않았다.
- 수정 후 Node 테스트 17개 모두 통과. Java 코드는 이번 최종 검증에서 변경하지 않았고
  앞선 Java 110개 통과 결과와 이번 실제 MySQL/브라우저 검증을 구분한다. Java 전체 재실행은 하지 않았다.
- 이번 추가 변경은 app.js, form-flow.test.mjs, 이 문서뿐이다. 기존 작업 04 변경을 모두 보존했다.
  HTTPS 프록시 및 다른 브라우저 엔진은 미검증이다. 커밋·푸시는 하지 않았다.
