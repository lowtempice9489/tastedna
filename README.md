# TasteDNA — 의류 취향 분석 및 결과 관리 (Simple V1)

TasteDNA는 쇼핑몰 이용자의 의류 취향을 질문 응답으로 관측하고, 그 근거를 설명 가능한 RESULT 후보·판별 결과와 연결하는 Java/Spring 기반 서비스입니다. **FORM**은 TasteDNA의 회원·상품·화면 연동을 검증하기 위한 데모 쇼핑몰입니다. TasteDNA만을 위한 별도 회원가입이 필요하지 않으며, 완성된 전자상거래 플랫폼이나 외부 쇼핑몰용 즉시 설치 SDK는 아닙니다.

이 README는 **2026-11-11 기준 `master`의 구현 상태**를 설명합니다. 저장소의 일부 `docs/` 문서는 이전 개발 단계에서 작성된 기록이므로, 각 문서의 작성 시점과 이 README의 현재 구현 상태를 구분해 읽어주세요.

## 주요 기능

- **남성복·여성복 Simple 테스트**: 각각 10문항. 단일·복수 선택, 비교 및 그룹 내 순위형 응답, NONE 배타 조건 등을 서버 정의에 따라 검증합니다.
- **회원 테스트**: FORM 계정으로 로그인해 응답 저장·수정, revision 충돌 검사, 진행 중 세션 복구·재개, 제출 및 본인 결과 재조회를 지원합니다.
- **비회원 Simple**: 남녀 테스트에 응답하고 즉시 결과를 확인할 수 있습니다. 응답·결과는 요청 처리 메모리에서 계산하며 회원 세션·답변·결과 보관 테이블에 저장하지 않습니다. 새로고침 후 복구·영구 저장은 지원하지 않습니다.
- **관측과 RESULT**: 선택·비교·순위에 담긴 실제 선호 및 Raw 관측을 추적하고, R01~R25 RESULT 후보 판별 로직을 제공합니다. 판별 근거가 충분할 때만 `FINAL`이며, 부족하면 `DEFERRED`입니다. **R01~R25 중 어느 유형이든 항상 FINAL이 된다는 뜻은 아닙니다.**
- **직접 선호의 분리**: Q02 직접 선택, Simple 대표 스타일 및 종합 RESULT는 서로 다른 개념입니다. Q02 단일 선택만으로 종합 RESULT를 확정하지 않습니다. 직접 선호 요약 API의 `comprehensiveResult=false`도 이 구분을 나타냅니다.
- **상품 연결**: FORM 데모 상품 100개를 대상으로 의류 범위와 Q02 스타일을 우선 적용하고, Q01 색상 일치 근거를 표시합니다. 색상이 일치하는 상품을 먼저 보여주되 그 외 스타일 일치 상품도 유지합니다. 점수·추천 정확도·매치율을 주장하지 않습니다.
- **마이페이지**: 최근 테스트 기록, 결과 리포트, 명시적 저장, 현재 TasteDNA 선택, 저장 해제 및 본인 결과 직접 삭제를 지원합니다.

남성복 매칭에는 MENS·UNISEX, 여성복에는 WOMENS·UNISEX를 허용하고 미분류 상품은 제외합니다. 이는 허용 규칙이며 실제 카탈로그에 해당 범위의 상품이 등록되어 있다는 의미는 아닙니다.

## RESULT 보관·삭제 정책

| 대상 | 정책 |
|---|---|
| 회원이 직접 저장한 결과 | **최대 5개**. 저장 상태인 동안 5일 자동 삭제 대상에서 보호 |
| 현재 TasteDNA | **회원당 최대 1개**. 종합 RESULT가 `FINAL`이고 대표 후보가 있는 결과만 새로 설정 가능. 저장 슬롯과 별도의 보호 상태 |
| 회원의 미저장·비현재 제출 결과 | 제출 후 **5일** 동안 임시 조회 가능. 만료 후 이력에서 제외·결과 접근 차단·자동 물리 삭제 대상 |
| 회원 직접 삭제 | 본인의 제출 결과만 삭제. 저장된 결과도 명시적 확인 후 삭제 가능. 현재 TasteDNA는 먼저 설정 해제 필요 |
| 기존 만료 표식이 없는 기록 | `temporary_result_expires_at IS NULL`인 V28 이전 기록은 소급 자동 삭제하지 않음. 본인이 직접 삭제하는 것은 가능 |
| 비회원 결과 | DB에 보존하지 않으며 회원 저장·복구 대상이 아님 |

5일 만료 후 자동 정리는 세션과 연결된 보관 행·답변을 삭제합니다. **90일 답변 보존/Deep 재사용 정책은 현재 적용하지 않습니다.**

자동 삭제 스케줄러는 `member-auth` 프로필에서 **명시적으로 활성화할 때만** 실행됩니다. 기본값은 비활성화이며, 활성화하면 기본 실행 주기는 **매일 03:00 UTC(한국 시간 12:00)**입니다.

```properties
# 검증된 환경에 한해 명시적으로 활성화
# tastedna.result-cleanup.enabled=true
# 선택 사항: 기본값 0 0 3 * * * (UTC)
# tastedna.result-cleanup.cron=0 0 3 * * *
```

운영 활성화 전에 DB 백업·권한·로그·복구 절차와 실제 배포 환경을 별도로 점검해야 합니다. `master`에 코드가 들어 있다는 이유만으로 스케줄러가 자동 활성화되지는 않습니다.

## FORM 시연 흐름

1. FORM 회원으로 가입·로그인하거나 비회원 Simple을 선택합니다.
2. 의류 → 남성복/여성복 → Simple에서 10문항에 응답하고 제출합니다.
3. 실제 선택 근거와 RESULT 판별 상태, 연결된 데모 상품을 확인합니다.
4. 회원이라면 마이페이지에서 최근 테스트 기록을 다시 열거나 결과를 저장합니다.
5. `FINAL` 조건을 충족한 결과는 현재 TasteDNA로 지정할 수 있습니다. 회원은 결과를 직접 삭제할 수도 있습니다.

비회원은 제출 직후 결과를 확인하는 흐름이며, 회원처럼 DB 기반 기록 복구·저장 기능을 제공하지 않습니다.

## 실행 환경 및 로컬 실행

- Java **21**, Spring Boot **4.1.1**, Spring Data JPA, MySQL **8.4**, Flyway, Gradle Wrapper
- 화면 자산에는 프로젝트 루트의 `gender/` 이미지 디렉터리가 필요합니다.
- Node.js는 프런트엔드 자동 테스트를 실행할 때 필요합니다.
- 현재 Flyway 스키마 이력의 최신 파일은 **V28**입니다. 기존 마이그레이션을 편집하거나 `flyway clean`으로 개발 DB를 초기화하지 마세요.

**중요:** 기본 설정 DB와 검증용 DB는 별개입니다. 로컬 검증은 기존 개발 데이터 대신 독립된 `tastedna_test`와 적절히 제한된 테스트 DB 계정으로 진행합니다. 아래 PowerShell 예시는 로컬 HTTP용입니다.

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/tastedna_test?connectionTimeZone=UTC'
$env:DB_USERNAME = Read-Host '테스트 DB 계정'
$secret = Read-Host '테스트 DB 비밀번호' -AsSecureString
try {
    $env:DB_PASSWORD = ([pscredential]::new('db', $secret)).GetNetworkCredential().Password
    $env:AUTH_COOKIE_SECURE = 'false'  # 로컬 HTTP에서만 사용
    .\gradlew.bat bootRun '--args=--spring.profiles.active=member-auth --server.address=127.0.0.1 --server.port=8081'
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable secret -ErrorAction SilentlyContinue
}
```

서버 시작 로그를 확인한 뒤 `http://127.0.0.1:8081`로 접속합니다. 운영 HTTPS 환경에서는 보안 쿠키 설정을 유지해야 하며, DB 비밀번호·인증 쿠키·CSRF 토큰을 Git에 기록하지 마세요. 자동 삭제는 위 실행 예시에서 활성화하지 않습니다.

## 개발 및 검증 현황

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test --tests 'com.tastedna.session.application.MemberResultCleanupServiceTest' --tests 'com.tastedna.member.MemberAuthHttpTest'
node --test dev/verification/form-flow.test.mjs
# 전체 Java 테스트
.\gradlew.bat test
```

테스트 명령이 MySQL에 연결되는 경우 **반드시 테스트용 DB를 먼저 지정**하세요. IntelliJ 실행 구성의 환경 변수가 별도 PowerShell 터미널에 자동 상속되는 것은 아닙니다. 일부 통합 테스트는 특정 개발용 정의·세션 데이터가 있어야 하므로, 단순히 DB 연결이 성공했다고 전체 테스트가 통과하는 것은 아닙니다.

**2026-11-11까지 실제 확인한 범위**

| 검증 대상 | 확인 결과와 범위 |
|---|---|
| 회원 RESULT 관리 | 저장 결과 5/5 및 기존 결과 기록 유지, 직접 삭제 확인창·삭제 후 최근 기록 감소를 브라우저에서 확인 |
| 회원 직접 삭제 정합성 | 테스트 DB에서 고아 답변 0건 확인. 단, 이 수치만으로 모든 삭제 시나리오를 증명하지는 않음 |
| 5일 만료 자동 삭제 | 격리된 테스트 계정의 만료·미보호 세션 1건에 대해 임시 매분 스케줄 실행 `deleted=1, failed=0` 확인. 해당 세션·답변·보관 행 잔존 0건 확인 |
| 저장 결과 보호 | 위 자동 삭제 검증 후 저장된 결과 5건이 유지되고 모두 `PROTECTED`로 조회됨. 만료된 저장 결과의 실DB 사례는 별도로 재현하지 않음 |
| 관련 단위·HTTP 테스트 | `MemberResultCleanupServiceTest`, `MemberAuthHttpTest` 선택 실행 성공. `DefaultSecurityRegressionTest` 단독 실행 성공 |
| 전체 Java 테스트 | **전체 성공 아님.** 로컬 전체 실행에서 340개 중 실패가 있었음. 고정된 과거 개발 세션·정의 및 채점 규칙에 의존하는 기존 통합 테스트 문제를 확인했으며 해당 테스트들은 현재 미정리 |

이 검증 기록은 특정 로컬 테스트 환경의 결과입니다. 모든 입력 조합·동시성·삭제 실패 롤백·외부 배포를 완전히 검증했다는 뜻은 아닙니다.

## 아직 제공하지 않는 범위

- **Deep 25문항** 및 그에 따른 종합 분석·재사용 흐름
- 외부 쇼핑몰 회원 인증·상품 카탈로그를 자동 연결하는 범용 SDK/플러그인 및 운영 배포 완료 상태
- 실제 주문·결제·배송, 이메일 인증·비밀번호 재설정, 다중 서버 세션 인프라
- 모든 RESULT의 무조건적 `FINAL` 보장 또는 추천 정확도에 대한 통계적 검증

## 관련 문서 및 구조

- [기존 Simple V1 제출 상태 기록](docs/v1-submission-status.md) — 이전 개발 단계 기록으로, 현재 README보다 과거 범위를 설명합니다.
- [쇼핑몰 연동·실행 가이드](docs/storefront-integration.md) — 인증·자산·API 경계. 일부 구현 상태·Flyway 버전은 작성 시점 기준입니다.
- [회원 인증·CSRF 계약](docs/member-auth-contract.md), [FORM 인증 UI](docs/form-frontend-auth.md)
- [질문·답변 계약](docs/question-answer-contract.md), [순위형 선택 계약](docs/ranked-comparison-contract.md)
- [초기 RESULT 후보 API](docs/simple-initial-candidates-api.md), [과거 FINAL 승인 검토 기록](docs/simple-result-finalization-gaps.md)
- [데모 상품 매칭](docs/demo-product-matching-api.md), [의류 범위](docs/demo-catalog-apparel-scope.md), [색상 매칭](docs/demo-catalog-colors.md)

코드상 `definition`은 질문 정의, `session`은 세션·답변·보관 및 만료, `scoring`은 Raw 관측과 RESULT 판별, `member`는 FORM 회원, `demo`는 데모 상품, `config`는 인증·자산·스케줄 설정을 담당합니다. DB 변경은 `src/main/resources/db/migration`에 있습니다. 구현 변경 시 [AGENTS.md](AGENTS.md)와 승인된 질문 계약을 함께 확인하세요.
