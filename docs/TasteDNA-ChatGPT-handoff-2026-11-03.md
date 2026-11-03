# TasteDNA 작업 현황 및 ChatGPT 수동 작업 인수인계

작성 기준: 2026-11-03 / 프로젝트: C:\Users\User\IdeaProjects\tastedna

## 1. 지금 어디까지 왔는가

TasteDNA는 기존 패션 쇼핑몰에 부가 서비스로 붙일 패션 취향 테스트다. 현재 FORM이라는 임시 쇼핑몰에서 **남성복 SIMPLE 10문항 → DB 답변 저장 → 제출 → 직접 선호 결과 화면**까지 동작한다. 사용자가 웹 미리보기에서 남성복 구현을 확인했다.

다음 목표는 여성복을 포함한 TasteDNA다. 최종 요청에 따라 질문 전 남성·여성 선택 입구까지만 구현했다. 남성은 기존 로그인·테스트로 연결하고 여성은 준비 중 안내 및 선택 화면 복귀만 제공한다. 여성 선택 시 API 호출이나 세션 생성은 하지 않는다. 여성복은 남성복보다 선택지가 많고 일부 질문 수정이 필요하다는 사용자 설명이 있다. 여성복 DB 정의·시작 API·실제 질문은 이번에 구현하지 않았다.

현재 결과는 최종 종합 TasteDNA 유형이 아니라 **사용자가 직접 고른 취향의 근거 기반 요약**이다. 25개 RESULT 후보의 공식 점수·가중치·임계값·분류 규칙은 임의로 만들지 않았다. SIMPLE=10문항, DEEP=25문항이라는 설계와 현재 전시 화면의 구현 범위를 혼동하면 안 된다.

## 2. 실행 및 localhost:8080 해결 현황

- Java 21 / Spring Boot 4.1.1 / Gradle / MySQL 8.4 / Spring Data JPA / Flyway.
- 로컬 주소: http://localhost:8080/ ; TasteDNA 소개: http://localhost:8080/#taste
- local 프로필로 실제 MySQL tastedna 개발 DB에 연결해 서버 실행에 성공했다.
- Flyway V13, V14, V15가 적용됐다. 기존 적용 마이그레이션을 수정하지 말고 이후 변경은 새 버전으로 추가한다.
- 2026-11-03 16:42:40 서버 재시작 로그에서 Tomcat 8080 및 Started TastednaApplication을 확인했다. 서버 실행 상태는 프로세스 종료·재부팅에 따라 달라진다.
- 로컬 체험 계정: dev-image-demo. 비밀번호는 사용자가 설정한 DEV_MEMBER_PASSWORD를 사용한다.
- 기존 dev-member-001, dev-member-002에는 이전 정의의 진행 세션이 있어 별도 체험 계정을 추가했다. 기존 세션/답변은 변경하거나 삭제하지 않았다.
- 인증값은 DB_USERNAME, DB_PASSWORD, DEV_MEMBER_PASSWORD 환경변수로 주입한다. 실제 비밀번호는 이 문서에 포함하지 않는다.

### 다시 실행하는 법

IntelliJ에서 JDK 21, working directory를 프로젝트 루트, active profile을 local로 설정하고 위 환경변수를 넣어 TastednaApplication을 실행한다. 또는 프로젝트 루트의 Start-TasteDNA.cmd를 실행한다. dev/start-local.ps1이 Java 21을 찾고 필요한 값을 입력받는다. 값은 프로세스에서만 사용하며 파일에 저장하지 않는다. 실행 창을 닫거나 Ctrl+C를 누르면 서버가 종료된다.

Gradle 직접 실행:

```powershell
# 먼저 IntelliJ 또는 현재 셸에 DB 환경변수를 설정한다. 공유용 명령에 비밀번호를 적지 않는다.
$env:JAVA_HOME='C:/Users/User/.jdks/temurin-21.0.11'
.\gradlew.bat --offline bootRun '--args=--spring.profiles.active=local --logging.file.name=build/local-server.log'
```

8080이 사용 중이면 기존 서버를 확인하고 두 번째 서버를 중복 실행하지 않는다. 로그는 build/local-server.log에 있다. offline 실행은 현재 PC의 캐시를 사용한 방식이며 새로운 PC에서는 의존성 다운로드가 필요할 수 있다. 개발 DB tastedna와 테스트 DB tastedna_test를 구분한다.

## 3. 구현한 사용자 흐름

1. FORM 쇼핑몰: 전시 상품 7개, 카테고리 필터, 상품 상세.
2. TasteDNA 소개 → 개발용 계정 연결.
3. 남성복 SIMPLE Q01~Q10: 텍스트/이미지/비교/조건부 후속 질문.
4. 다음 버튼으로 답변 저장, 전체 선택 검토 및 수정.
5. 제출 후 직접 선호 기록: 색·스타일·핏·패턴·선택 착장·비교 선택·명시적 회피.
6. 취향 기록 텍스트 다운로드 및 쇼핑몰 복귀.

상품명·가격은 전시용 예시다. 결제·주문·재고·실제 상품 추천은 구현하지 않았다. 인증은 local 개발용 Basic 인증이며 실제 쇼핑몰 회원 인증 연동은 별도 작업이다. 인증정보는 브라우저 저장소에 보관하지 않고 메모리에서만 사용한다. 새로고침 시 다시 로그인하고 URL의 세션 ID를 통해 저장된 응답을 복원한다.

회원당 진행 중 세션 하나라는 기존 규칙을 유지한다. 기존 세션이 있으면 이어가며, 이전 정의의 답변에 새 이미지나 새 문항을 덧붙이지 않는다.

## 4. 확정·구현된 남성복 질문 계약

정의 ID: 33333333-3333-4333-8333-333333333333 / SIMPLE-MENS-IMAGE-V2.
기존 활성 정의는 보존하고 전용 시작 경로에서 서버가 이 정의를 선택한다.

| 문항 | 현재 구현 |
| --- | --- |
| Q01 | 색상 텍스트, 최대 5개 |
| Q02 | 스타일 텍스트, 최대 4개 |
| Q03 | 실루엣 텍스트, 최대 3개 |
| Q04 | 패턴 텍스트, 최대 4개 |
| Q05 | 착장 A~F 중 1개 |
| Q06 | 의류 A~G 중 1개 |
| Q07 | 회피 착장 A~G 최대 2개 또는 NONE 단독 |
| Q08 | C01 A/B, C02 A/B/C, 조건부 C02_POCKETS A/B, C03 A/B/C |
| Q09 | C01 A/B 그룹, C02 A~E, C03 A/B 그룹 |
| Q10 | 착장 A~J 중 1개 |

Q01~Q04에 이미지가 없는 것은 의도된 구성이다. Q06/Q07 A~G 연결 및 Q07 최대 2개·NONE 단독은 사용자 승인 사항이다. Q06의 과거 P코드와 현재 이미지 A~G를 임의로 대응시키지 않는다.

Q08 C02에서 B/C 선택 시 포켓 개수 후속 질문이 필수다. A로 바꾸면 후속 응답을 제거하며 서버도 숨겨진 후속 응답을 거부한다. 비교는 comparisonId + selectedOptionId를 보존한다. Q09 그룹 이미지는 그룹 전체가 한 선택지다. Q10은 이전 응답을 덮어쓰지 않는다.

## 5. DB 및 결과 처리

- V13__add_mens_image_simple_definition.sql: 남성복 질문·선택지·이미지 경로·조건·직접 관측 규칙.
- V14__move_remaining_scoring_configuration_to_db.sql: 파일에서 읽던 메타데이터 4개를 td_scoring_configuration으로 이전.
- V15__create_demo_store_catalog.sql: td_demo_product에 전시 상품 저장.
- 런타임 데이터 접근은 JPA를 사용한다. 실행 코드가 dev/fixtures JSON 파일을 설정 원본으로 읽지 않는다.
- 질문/답변 스냅샷 및 중첩 구조의 DB JSON 컬럼은 유지한다. ‘모든 JSON 표현을 제거’한 것이 아니라 불필요한 파일 의존을 DB로 옮겼다. fixture JSON은 테스트와 이력 확인용으로 남아 있다.
- 결과는 제출된 답변의 presented_payload 및 answer_revision에 기반해 재구성한다. 별도 확정 RESULT 테이블을 새로 만든 것은 아니다.
- 결과 상태 DIRECT_PREFERENCE_SUMMARY, comprehensiveResult=false.
- 선택하지 않은 옵션을 비선호로 해석하지 않는다. Q07 NONE은 명시적 회피 근거 없음이며 모든 착장을 좋아한다는 뜻이 아니다.
- 원시 관측 파이프라인과 기존 후보 분석 코드는 존재하지만 새 이미지 정의의 최종 분류 완성을 뜻하지 않는다. /raw-observations는 개발 검증 인터페이스다.
- 기존 /simple-result는 이미지 v2에 대해 422 USE_PREFERENCE_SUMMARY_FOR_IMAGE_DEFINITION으로 잘못된 과거 매핑 사용을 막는다.

## 6. 주요 파일과 역할

아래 경로는 프로젝트 루트 기준이다.

| 파일 또는 경로 | 역할 |
| --- | --- |
| src/main/resources/static/index.html | 쇼핑몰·TasteDNA 기본 화면 |
| static/app.js, app.css, assessment-state.mjs | 질문·선택·저장·검토·결과와 상태 규칙 (static은 위 디렉터리) |
| static/shop.mjs, shop.css | 전시 쇼핑몰 화면 |
| src/main/java/com/tastedna/session/api/ImageAssessmentController.java | 이미지 SIMPLE 시작 API, 현재 남성복 고정 |
| session/application/TestSessionService.java | 정의 선택·세션 시작·기존 세션 보호 (Java 경로는 com/tastedna 아래) |
| session/application/comparison/ComparisonAnswerValidator.java | 비교·다지선다·조건부 유효성 검사 |
| scoring/application/SessionObservationService.java | 활성 비교 선택의 관측 연결 |
| scoring/application/PreferenceSummaryService.java | 소유권·제출 여부·스냅샷 기반 요약 |
| scoring/api/PreferenceSummaryController.java | 저장 답변 및 요약 API |
| scoring/application/ScoringConfigurationService.java | DB 설정 읽기 |
| scoring/domain/ScoringConfiguration.java 및 persistence/ScoringConfigurationRepository.java | 설정 엔티티/JPA |
| scoring/application/StyleCoreCandidateService.java, VisualEvidenceReadinessEvaluator.java | 기존 분석 설정의 파일 의존 제거 |
| scoring/api/SimpleResultController.java | 이미지 v2의 과거 결과 경로 사용 방지 |
| config/QuestionAssetConfig.java | local 이미지 경로 제공 |
| config/SecurityConfig.java | local 화면/API 접근 및 개발 계정 |
| demo/ | 전시 상품 엔티티·저장소·API |
| src/main/resources/db/migration/ | Flyway 변경 이력 |
| docs/storefront-integration.md | 실행·API·질문 계약 상세 |
| docs/question-answer-contract.md, scoring-contract.md, image-production-spec.md | 기존 설계 문서, 변경 전 함께 대조 |
| dev/start-local.ps1, Start-TasteDNA.cmd | 로컬 실행 도우미 |

추가 API(local): GET /api/v1/demo/products, POST /api/v1/sessions/simple-image,
GET /api/v1/sessions/{id}/answers, GET /api/v1/sessions/{id}/preference-summary.
답변 저장과 제출은 기존 세션 API의 POST /answers, POST /submit을 이용한다.
회원 식별자는 Authentication에서 얻는다. 클라이언트가 임의의 definitionId나 채점 규칙을 주입하도록 바꾸지 않는다.

## 7. 여성복 확장 — 선택 입구만 구현, 실제 질문은 미구현

이미지 원본은 gender/female 아래에 있다. 다음은 **파일명/폴더를 조회한 목록**이다. 이미지가 있다는 사실만으로 질문 의미·선택 규칙·채점 근거가 승인된 것은 아니다.

| 문항 | 확인된 여성복 파일 구조 | 다음 작업에서 확정할 것 |
| --- | --- | --- |
| Q01~Q04 | 여성복 이미지 폴더 없음, 텍스트 문항 영역 | 남성복과 질문·선택지·최대 개수를 공유할지 |
| Q05 | A.png~J.png, desktop/mobile 합본 | 10개 선택지의 의미·질문 문구·선택 개수 |
| Q06 | A.png~H.png, 합본 | 8개 코드 대응 및 문구 |
| Q07 | A.png~K.png, 합본 | 11개 회피 선택지, 최대 개수·NONE 적용 여부 |
| Q08 C01 | A1~A3, B1~B3 및 합본 | A/B 각 3장을 그룹 선택으로 볼지 |
| Q08 C02 | A~D 개별 및 합본 | 4개 선택지 의미·규칙 |
| Q08 C03 | A/B 개별 및 합본 | 비교 의미 |
| Q08 C04 | A_미니.png, B_knee_individual 이미지, C_롱.png 및 합본 | 3개 옵션의 코드·표시명·조건 여부 |
| Q08 C05 | A~D 개별 및 합본 | 비교 의미·4개 선택지·조건 여부 |
| Q09 C01 | A1~A4/B1~B4 및 합본 | 그룹 비교 의미 |
| Q09 C02 | A~E 및 합본 | 5개 선택지 의미 |
| Q09 C03/C04 | 각각 A1~A4/B1~B4 및 합본 | 두 비교의 의미와 각각의 그룹 단위 |
| Q10 | A.png~S.png 및 Q10_AS 합본 | 19개 전체 사용 여부·최종 문항 규칙 |

여성복은 남성복과 옵션 수, Q08/Q09 비교 수가 달라 경로만 male→female로 치환해서 완성할 수 없다. 원본 DOCX는 남성복 작업 상태를 정리한 자료이므로 여성복에 그대로 승인된 것으로 간주하지 않는다.

### 제안하는 다음 순서 (미구현)

1. 원본 이미지와 추가 여성복 문서를 확인해 Q01~Q10 계약표를 먼저 확정한다. 각 항목은 questionId, comparisonId, optionId, 표시문구, 이미지 경로, 선택 개수, 조건 관계를 가진다.
2. 남성·여성 선택 입구는 구현했다. 여성 준비 중 안내를 승인된 여성복 질문 흐름으로 연결하는 것이 다음 작업이다. MENS/WOMENS는 사용자의 성별 판정이나 취향 점수 속성이 아니다.
3. 진행 세션이 있을 때 다른 의류 범위를 선택하는 동작을 결정한다. 현재 회원당 진행 세션 하나 규칙을 조용히 변경하지 않는다.
4. 서버에서 허용된 의류 범위에 대응하는 정의를 선택하도록 확장한다. 여성복은 별도 정의 ID/버전과 새 마이그레이션으로 추가하고 기존 남성복 정의/제출 답변을 유지한다.
5. 기존 질문 렌더러·검증기·요약 서비스를 재사용하되 남성복 표시문구와 고정 정의 선택 지점을 확인한다. 여성복 비교 개수와 조건부 문항을 테스트한다.
6. 여성복 직접 선호 결과까지 검증한다. 공식 종합 분류는 승인된 설계가 확보된 후 별도로 구현한다.

## 8. 검증 이력 및 한계

2026-11-03 작업에서 다음을 확인했다.

- 관련 Java 테스트 84개 통과, 실패/오류 0. build/test-results/test의 11개 XML 보고서에서 재확인했다. 전체 프로젝트의 모든 통합 테스트가 통과했다는 뜻은 아니다.
- Node assessment-state 테스트 6개 통과, app.js/shop.mjs 문법 검사 통과 (앞선 실행 이력).
- 개발 계정 추가 이후 gradlew classes 성공 및 DevTools 재시작 성공.
- 실제 MySQL 개발 DB에 V13~V15 적용 성공.
- 실제 브라우저로 쇼핑몰·상품 상세·이미지·남성복 10문항 저장·검토·제출·결과를 확인했다.
- Q07에서 착장 A를 고른 뒤 NONE을 선택하면 A가 해제됨을 확인했다.
- Q08 포켓 선택 시 후속 포켓 개수 문항이 나타나고 저장·결과에 함께 반영됨을 확인했다.
- 검증용 새 세션: e5c48168-d080-4c74-8966-796455f18eb7 / dev-image-demo. 제출 완료. 이 답변은 테스트용 선택이며 사용자의 실제 취향 데이터가 아니다.
- 기존 개발 계정 세션은 수정하지 않았다. 실제 브라우저 검증은 별도 새 회원의 개발 DB 세션으로 수행했으며 isolated test DB에서 수행한 것은 아니다.

재현 명령은 docs/storefront-integration.md의 검증 항목을 따른다. 최종 요청으로 app.js의 입구만 변경했다. 결과의 새 기록 시작도 선택 입구로 연결한다. 기존 #session 링크 복원과 DB·채점 로직은 유지했다. 입구 변경 검증은 아래 추가 기록을 참고한다.

미완료: 여성복 흐름, 전시 UI의 DEEP 흐름, 확정된 25종 RESULT 분류, 개인화 상품 추천, 운영 인증/배포, 전체 반응형·접근성 검증. 일부 이미지와 화면 확인이 전체 자산·모든 선택 조합의 검증을 뜻하지 않는다.

이미지는 프로젝트 gender 폴더에서 /assets/gender/**로 제공한다. JAR만 복사하면 이미지가 포함되지 않는다. 파일 자체를 덮어쓰면 이전 결과 이미지도 달라질 수 있으므로 향후 자산 버전 관리가 필요하다.

## 9. ChatGPT에 전달할 자료

ChatGPT는 이 문서의 PC 경로나 localhost에 직접 접근할 수 없다. 필요한 파일을 채팅에 첨부하거나 내용을 붙여 넣어야 한다.

우선 이 문서, AGENTS.md, docs/storefront-integration.md, 여성복 질문 설명 자료와 Q05~Q10 합본 이미지를 전달한다. 구현 단계에서 위 주요 소스 및 V13~V15를 추가 제공한다. 전체 이미지가 많으면 Q05/Q06부터 문항별로 나누어 전달한다. 코드 수정 요청 시 부분 코드만 추측하게 하지 말고 관련 파일 전체를 전달한다.

원본 자료 위치:
- D:/AI 교육/TasteDNA(테이스트DNA)/인텔리제이 코딩/이미지들/TasteDNA Simple Q01~Q10 질문별 원문과 후속 결정.docx
- D:/AI 교육/TasteDNA(테이스트DNA)/인텔리제이 코딩/원칙·규칙/제작 규칙 1.txt ~ 제작 규칙 5.txt

현재 Git에는 기존 수정 파일과 미추적 파일이 다수 있다. 이번 작업 내용은 커밋·푸시하지 않았다. gender와 새 소스가 미추적 상태여서 저장소의 기존 커밋만 전달하면 최신 구현을 받을 수 없다. 루트의 요청 검증 JSON 등 기존 미추적 파일은 임의 삭제하지 않았다. 공유할 때 .env, 실제 비밀번호, 개인 DB 덤프, 불필요한 로그는 제외한다.

## 10. ChatGPT 새 채팅 시작문 (복사해서 사용)

> 나는 기존 쇼핑몰에 추가할 패션 취향 서비스 TasteDNA를 개발하고 있습니다. 첨부한 인수인계 문서와 AGENTS.md를 먼저 읽고 현재 완료 사항과 미완료 사항을 구분해주세요. 현재 남성복 SIMPLE 10문항, 이미지 연결, DB 답변 저장, 직접 선호 결과 화면은 localhost에서 검증됐습니다. 다음 목표는 여성복까지 포함하는 것입니다.
>
> 남성·여성 선택 입구와 여성 준비 중 안내는 이미 있습니다. 먼저 코드를 생성하지 말고 여성복 질문 계약표와 선택 이후 흐름을 정리해주세요. 여성복 이미지의 선택지 수와 비교 묶음은 남성복과 다릅니다. 파일명만으로 의미나 채점 규칙을 확정하지 말고, 내가 제공하는 이미지와 문서로 확인해주세요. 불명확한 항목만 모아 질문해주세요. MENS/WOMENS는 의류 범위입니다.
>
> 내가 구현을 요청하면 IntelliJ에서 수동 적용할 수 있도록 변경 파일 경로, 변경 전후 또는 파일 전체 내용, 적용 순서, 새 Flyway 마이그레이션, 검증 방법을 제공해주세요. 기존 남성복 정의와 답변을 보존하고 JPA/DB 중심 구조를 유지해주세요. 적용된 V13~V15는 수정하지 마세요. 없는 최종 분류 공식·점수·가중치를 만들어내지 말고, 직접 선호 요약과 최종 RESULT를 구분해주세요. 로컬 파일 접근이나 테스트 실행을 실제로 하지 않았다면 했다고 말하지 마세요.

## 11. 최종 추가 검증 — 선택 입구

최종 변경 파일은 src/main/resources/static/app.js와 이 문서다. 기존 상세 문서에도 입구 상태를 덧붙였다.
node --check 통과, Gradle processResources 성공. 실제 localhost 브라우저에서 소개 → 남성·여성 선택, 여성 → 준비 중 안내 → 다시 선택, 남성 → 기존 로그인 연결을 확인했다. 이 변경 검증에서는 새 답변 세션을 만들지 않았다. 기존 남성복 전체 흐름 검증은 8절의 앞선 결과이며 입구 변경 후 전체 10문항을 재실행한 것은 아니다.

## 12. 후속 정리 — 미사용 JSON 제거

2026-11-03 사용처 확인 후 미사용 JSON 15개를 제거했다. 루트 수동 API 요청 8개, 테스트에서 참조하지 않는 질문/관측 초안 4개, 이전 실험 메타데이터 3개다. 상세 목록과 남긴 fixture의 용도는 dev/fixtures/README.md에 있다. 앞선 9절의 루트 요청 JSON 보존 설명은 이 정리 이전 상태다. 테스트에서 실제 읽는 JSON, 재현용 SQL, 적용된 Flyway 이력, 남성복/여성복 이미지와 DB 데이터는 유지했다.
