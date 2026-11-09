# TasteDNA V1

TasteDNA는 쇼핑몰 회원의 의류 취향 응답을 저장하고, 설명 가능한 직접 선호와 Raw 관측으로 연결하는 Java/Spring 서비스입니다. FORM은 이를 시연하는 데모 쇼핑몰입니다. TasteDNA용 별도 회원가입을 요구하는 서비스나 완성된 전자상거래 플랫폼은 아닙니다.

## 현재 제공 범위

- 남성복·여성복 Simple 각각 10문항: 질문 정의에 따른 단일·복수·비교·순위형 선택과 NONE 배타 응답
- 회원 소유 세션의 답변 저장·수정, revision 충돌 검사, 새로고침 복구·재개·제출
- 실제 선택으로 생성한 Raw 관측과 직접 선호 리포트, FORM 마이페이지 기록·결과 재조회
- Q02 R01~R12 직접 스타일 선택: 복수 선택은 동등하게 표시하며 대표 순위를 만들지 않음
- 데모 상품 100개: 의류 범위와 Q02 스타일로 후보 선정, Q01 색상 일치 근거 제공
- 색상 일치 상품 우선 배치. 나머지 스타일 일치 상품도 유지하고 그룹 내부는 상품 ID 순서 유지

남성복 세션은 MENS·UNISEX, 여성복은 WOMENS·UNISEX만 허용합니다. 미분류 상품은 제외합니다. 현재 등록 데이터에는 UNISEX 상품이 없으며, 지원 정책과 실제 등록 수를 구분합니다.

**직접 선호는 최종 종합 RESULT가 아닙니다.** `preference-summary`는 `comprehensiveResult=false`, `initial-candidates`의 최종 판별은 `DEFERRED`입니다. Q02 단일 선택도 FINAL로 승격하지 않습니다. 상품 매칭은 RESULT 유형이나 이미지 URL이 아니라 등록 속성에 기반합니다.

## TasteDNA 시연 흐름

TasteDNA는 기존 쇼핑몰에 취향 테스트 기능을 연결하는
상황을 가정해 개발한 프로젝트입니다.
FORM은 기능을 확인하기 위한 데모 쇼핑몰입니다.

1. FORM 회원가입 후 로그인합니다.
2. TasteDNA → 의류 → 남성복 또는 여성복 → Simple을 선택합니다.
3. 10개 질문에 응답하고 제출합니다.
4. 직접 선택한 스타일과 색상 등 취향 기록을 확인합니다.
5. Q02 스타일과 Q01 색상 근거로 연결된 FORM 전시 상품을 확인합니다.
6. 마이페이지에서 완료 기록과 결과 리포트를 다시 조회합니다.

Q02에서 유효한 스타일을 하나만 선택했다면
Simple 대표 스타일이 표시됩니다.
여러 스타일을 선택한 경우 임의로 대표 순위를 만들지 않습니다.

Simple 대표 스타일은 최종 종합 RESULT가 아닙니다.

### 구현에서 중요하게 고려한 사항

- 회원별 테스트 기록 접근을 제한하고 저장·제출 시 답변 버전을 관리합니다.
- 실제 응답에서 관측한 선호와 추론되지 않은 정보를 구분합니다.
- 스타일과 상품의 등록 속성을 근거로 매칭 결과를 설명합니다.
- 결과 판별 근거가 부족하면 임의의 최종 유형을 생성하지 않습니다.
- 기존 쇼핑몰의 회원·상품 시스템과 연결할 수 있도록 기능별 책임을 구분합니다.

현재 구현은 포트폴리오용 Simple V1이며,
외부 쇼핑몰에 즉시 설치할 수 있는 SDK나 운영 서비스는 아닙니다.

자세한 검증 결과와 구현 범위는
[Simple V1 제출 상태](docs/v1-submission-status.md)를 참고하세요.

## 실행

Java 21, MySQL 8.4, Spring Boot 4.1.1, Spring Data JPA, Flyway를 사용합니다. Gradle Wrapper를 사용하며 최초 실행에는 의존성 다운로드가 필요합니다. Node.js는 프런트엔드 테스트에 필요합니다.

저장소를 복제한 뒤 프로젝트 루트에서 실행하세요. `gender/` 이미지 폴더도 필요합니다. 기본 설정의 DB는 **tastedna**이므로 아래 URL 재지정 없이 테스트 서버를 실행하지 마세요. 사전에 독립 `tastedna_test` DB와 그 DB에만 권한이 있는 계정을 준비합니다. 기존 DB를 초기화하지 않습니다.

```powershell
# JAVA_HOME은 자신의 Java 21 설치 경로로 지정
$env:SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/tastedna_test?connectionTimeZone=UTC'
$env:DB_USERNAME=Read-Host '테스트 DB 계정'
$secret=Read-Host '테스트 DB 비밀번호' -AsSecureString
try {
    $env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', $secret).Password
    $env:AUTH_COOKIE_SECURE='false' # 로컬 HTTP에서만 사용
    .\gradlew.bat bootRun '--args=--spring.profiles.active=member-auth --server.address=127.0.0.1 --server.port=8081'
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    $secret=$null
}
```

서버 기동 로그와 실제 HTTP 응답을 확인하고 http://127.0.0.1:8081 에서 FORM 회원가입 후 명시적으로 로그인합니다. Gradle 종료 메시지만으로 서버가 실행 중이라고 판단하지 않습니다. 비밀번호·쿠키·CSRF 토큰을 코드나 Git에 기록하지 마세요.

기동 시 Flyway가 V1~V25 중 미적용 마이그레이션을 적용하고 JPA가 스키마를 검증합니다. 기존 이력 파일 수정이나 Flyway clean은 사용하지 않습니다. HTTPS 배포에서는 `AUTH_COOKIE_SECURE=true`를 유지해야 합니다.

## 개발 및 테스트

```powershell
.\gradlew.bat compileJava
# 변경 영역만 선택한 예시; 실제 DB에 연결하지 않는 매칭 서비스 테스트
.\gradlew.bat test --tests 'com.tastedna.demo.DemoProductMatchServiceTest'
node --test dev/verification/assessment-state.test.mjs
```

전체 Java 테스트 명령은 `.\gradlew.bat test`입니다. 선택적 MySQL 테스트가 건너뛰어졌다면 DB 검증 성공이 아닙니다. 실제 DB 검증에는 별도 환경변수와 테스트 계정 제약이 있습니다. [실행·연동 가이드](docs/storefront-integration.md)의 테스트 절을 먼저 확인하세요. `--offline`은 의존성이 이미 캐시된 환경에서만 추가합니다.

## 구현·검증 상태

| 범위 | 현재 상태 |
|---|---|
| 남녀 Simple 저장·복구·제출·리포트·마이페이지 | 실제 FORM 브라우저 대표 흐름 확인(15-A2·15-A3) |
| MySQL V25·JPA·회원 인증 통합 | 로컬 통합 테스트 재실행 성공 사용자 확인(15-A1F 후속) |
| Q02 스타일·Q01 색상 근거 표시 | 남녀 완료 세션 브라우저 확인 |
| Q01 색상 일치 우선 정렬 | Java 매칭 테스트 10개 통과(15-T3B3); 변경 후 실제 브라우저·MySQL 순서 확인은 미실행 |
| 소유권·의류 범위·UNISEX·미분류 경계 | 관련 자동 테스트 보유; 15-A3 브라우저에서 타인 접근과 상품 범위 DB 대조는 미검증 |
| 종합 RESULT R01~R25 확정, Deep 25문항 | 미완성; 직접 선호 결과와 구분 |
| 외부 쇼핑몰 인증·상품 시스템 자동 연결, 운영 배포 | 별도 통합 필요. SDK·설치 플러그인·자동 연동 API 없음 |

점수·매치율·추천 정확도를 검증한 서비스로 설명하지 않습니다. 주문·결제·배송, 이메일 인증·비밀번호 재설정, 분산 세션 운영도 구현 범위 밖입니다.

## 문서와 구조

- [외부 쇼핑몰 실행·연동 경계](docs/storefront-integration.md)
- [회원 인증·CSRF 계약 및 구현 이력](docs/member-auth-contract.md), [FORM 인증 화면 연결](docs/form-frontend-auth.md)
- [질문·답변 계약](docs/question-answer-contract.md), [순위형 계약](docs/ranked-comparison-contract.md)
- [초기 후보 API](docs/simple-initial-candidates-api.md), [최종 판별에 남은 승인 조건](docs/simple-result-finalization-gaps.md)
- [상품 매칭 API](docs/demo-product-matching-api.md), [의류 범위 근거](docs/demo-catalog-apparel-scope.md), [색상 근거](docs/demo-catalog-colors.md)

`definition`은 질문 정의, `session`은 세션·답변, `scoring`은 관측·후보, `member`는 FORM 회원, `demo`는 데모 상품, `config`는 보안·자산 설정입니다. DB 변경은 `src/main/resources/db/migration`, 독립 검증 자료는 `dev/fixtures`에 있습니다. 수정 시 [AGENTS.md](AGENTS.md)와 승인된 질문 계약을 준수하세요.
