# 카탈로그 커버리지 점검 (14-T2D)

아래 수치는 V15·V21·V22 등록 데이터와 매칭 서비스 테스트 기준이다. 실제 DB 적용은 미검증이다.

상품 100개, URL 100개, SHA-256 기준 이미지 내용 97개. 동일 내용 이미지 3쌍(6개 상품)은 서로 다른 단품을 표시한다.

- look-08 / look-91: 스트라이프 니트 / 베이지 슬랙스
- look-15 / look-96: 네이비 가디건 / 그레이 플리츠 스커트
- look-30 / look-56: 블랙 후드 집업 / 블랙 카고 팬츠

해시가 다른 파일도 유사한 착장을 포함할 수 있으므로 97개를 서로 다른 촬영·착장 수로 해석하지 않는다.

| 코드 | 매칭 상품 수 |
|---|---:|
| R01 | 10 |
| R02 | 0 |
| R03 | 12 |
| R04 | 6 |
| R05 | 16 |
| R06 | 8 |
| R07 | 7 |
| R08 | 0 |
| R09 | 11 |
| R10 | 2 |
| R11 | 1 |
| R12 | 1 |

스타일 미등록은 26개다. V21·V22에 기록한 단품 판단 보류 항목으로, 이미지나 category만으로 스타일을 확정하지 않았다.
R02와 R08의 0개는 명시적 상품 태그 부재 때문이다. 이름 유사성이나 분포 균등화를 근거로 수정하지 않았다.

## 테스트 DB에서 실행

기존 회원 MySQL 테스트의 고정된 tastedna_test/coql 사전 확인·Flyway 적용·JPA validate를 재사용한다.
아래 메서드 하나만 실행한다. 이전 V19 기대 테스트나 전체 회원 테스트를 실행하지 않는다.
기존 데이터 삭제·초기화는 하지 않는다.

```powershell
Set-Location 'C:\Users\User\IdeaProjects\tastedna'
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\temurin-21.0.11"
$env:DB_USERNAME = 'coql'
$env:RUN_MEMBER_MYSQL_TESTS = 'true'
$credential = Get-Credential -UserName 'coql' -Message 'tastedna_test 비밀번호 입력'
try {
    $env:DB_PASSWORD = $credential.GetNetworkCredential().Password
    .\gradlew.bat test --tests com.tastedna.member.MemberAuthMysqlIntegrationTest.demoCatalogV25HasExpectedStyleCoverage --rerun-tasks --offline
} finally {
    Remove-Item Env:DB_PASSWORD, Env:RUN_MEMBER_MYSQL_TESTS -ErrorAction SilentlyContinue
    $credential = $null
}
```

DB 테스트는 V22 적용·실제 JPA 카탈로그 수·태그·경로와 스타일별 Repository 조회를 검증한다.
Raw 매칭 서비스의 12개 스타일별 결과·재현성·다른 이미지 및 불일치 제외는 별도 단위 테스트로 검증한다.

## V25 다속성 검증 — 15-A1

위 명령은 현재 V25·상품 100개·MENS 29/WOMENS 71·색상 등록 70/미등록 30과 JPA 매핑을 검증한다. UNISEX와 범위 미분류는 실제 등록 상품이 없어 단위 테스트로만 검증한다.
남녀 제출·추천 API까지 확인하려면 같은 로컬 환경에서 Gradle 명령을 다음으로 대체한다. 새 mysql-a/mysql-b UUID 테스트 회원과 세션을 tastedna_test에 추가하며 기존 행은 보존한다.

```powershell
.\gradlew.bat test --tests com.tastedna.member.MemberAuthMysqlIntegrationTest.demoCatalogV25HasExpectedStyleCoverage --tests com.tastedna.member.MemberAuthMysqlIntegrationTest.realMembersCompleteImageFlowWhileCrossMemberReadsAndWritesAreDenied --rerun-tasks --offline
```

Codex 실행 환경에는 DB_USERNAME/DB_PASSWORD가 없어 실제 DB 검증을 실행하지 못했다. 브라우저는 FORM 로그인 화면이므로 카드·더 보기·색상 근거·마이페이지 재진입도 미검증이다. 실행 후 build/test-results/test/TEST-com.tastedna.member.MemberAuthMysqlIntegrationTest.xml에서 실패 0뿐 아니라 skipped 0과 실제 실행 건수를 확인해야 한다. BUILD SUCCESSFUL만으로 MySQL 통과로 판단하지 않는다.
