# TasteDNA 작업 02 — 공통 순위형 비교 구현 및 인계

작성: 2026-11-04. 이번 작업의 명시적 요청을 기준으로 기존 인수인계서의
“미선택은 비선호가 아니다” 원칙에 **순위형으로 지정된 그룹 내부만** 예외를 추가한다.
일반 복수선택과 기존 단일 비교에는 예외를 적용하지 않는다.

## 구현 범위

- 선택 순서가 순위인 비교 그룹을 기존 COMPARISON 구조에 추가했다.
- 선택 해제 시 후속 순위를 당기고, 재선택 시 마지막 순위로 추가한다.
- 기존 JSON 답변 컬럼과 presented_payload 스냅샷을 재사용한다.
- 서버 검증, 저장·재조회, 화면 복원, 검토, 직접 선호 결과와 텍스트 다운로드에 순서를 유지한다.
- 순위형 미선택은 서버가 스냅샷의 전체 옵션과 선택 목록을 비교해 산출한다.
- 순위·비선호 사실만 Raw에 보존하며 RESULT 점수·가중치·분류는 추가하지 않았다.
- 기존 정의, 답변, DB, Flyway V1~V15는 변경하지 않았다. 새 마이그레이션도 없다.

## 정의 인터페이스

질문의 `kind`는 기존 `COMPARISON`이다. `comparisons` 안의 대상 그룹에만 다음 필드를 추가한다.

```json
{
  "id": "C02",
  "label": "순위형 비교",
  "selectionMode": "RANKED_MULTI_SELECT",
  "minSelections": 1,
  "options": [
    {"id": "A", "label": "항목 A"},
    {"id": "B", "label": "항목 B"},
    {"id": "C", "label": "항목 C"}
  ]
}
```

여기서 `minSelections: 1`은 **테스트 예시일 뿐 최종 제품 정책이 아니다**.
실제 적용 전에 그룹별 최소 수를 승인받아 명시해야 한다. 기본값은 없다.
미지정·null·문자열·소수·음수·옵션 수 초과는 `INVALID_COMPARISON_DEFINITION`이다.
0부터 전체 옵션 수까지 정수 설정을 지원한다. 0으로 명시한 그룹만 빈 배열을 허용하고
화면에서 “이 비교에서 선호하는 항목 없음” 버튼을 제공한다.
필수 그룹의 답변 항목 자체를 생략할 수는 없다. 별도 최대 수는 두지 않고 전체 옵션 수까지 허용한다.

`selectionMode`가 없거나 `SINGLE`이면 기존 단일 비교다. 알 수 없는 모드는 정의 오류다.
기존 `showWhen` 단일 부모 조건을 유지하며 순위형 자식 그룹도 사용할 수 있다.
순위형 그룹을 조건의 부모로 사용하는 의미는 이번에 정의하지 않았으므로 서버가 거부한다.

## 답변 JSON과 기존 API

기존 `POST /api/v1/sessions/{id}/answers`의 `answerPayload`:

```json
{
  "selections": [
    {"comparisonId": "C01", "selectedOptionId": "A"},
    {"comparisonId": "C02", "selectedOptionIds": ["B", "A"]}
  ]
}
```

C02의 1위는 B, 2위는 A다. C는 해당 그룹 안에서 비선호다.
중복·미등록 ID·중복 그룹·누락/추가/숨겨진 그룹·단일/배열 필드 불일치를 거부한다.
각 선택 항목은 comparisonId와 해당 방식의 선택 필드 하나만 허용하므로
클라이언트 점수나 notPreferred 등 임의 근거도 거부한다.

저장 요청의 questionId·expectedRevision, 인증 회원 소유권, 서버의 정의 선택은 기존 계약이다.
`GET /api/v1/sessions/{id}/answers`는 배열 순서를 유지해 반환한다.
프론트엔드는 기존 `choose`, `selectionMap`, `valid`, `activeGroups`를 사용한다.
`selectionMap`의 값은 단일 비교에서 문자열, 순위형에서 순서가 있는 배열이다.

## 직접 선호 요약과 Raw

`GET /api/v1/sessions/{id}/preference-summary`의 순위형 Evidence:

```json
{
  "questionId": "Q09",
  "comparisonId": "C02",
  "prompt": "순위형 비교",
  "meaning": "RANKED_COMPARATIVE_PREFERENCE",
  "answerRevision": 9,
  "selections": [
    {"optionId": "B", "label": "항목 B", "images": [], "rank": 1},
    {"optionId": "A", "label": "항목 A", "images": [], "rank": 2}
  ],
  "alternatives": [],
  "notPreferred": [{"optionId": "C", "label": "항목 C", "images": []}]
}
```

기존 단일 비교는 `COMPARATIVE_PREFERENCE`와 중립적인 `alternatives`를 유지한다.
일반 복수선택에는 notPreferred가 없다. null인 rank와 빈 notPreferred는 JSON에서 생략하므로
기존 남성복 응답 형태를 보존한다. 전체를 선택한 순위형도 notPreferred를 생략한다.
비선호의 출처는 순위형 선택 목록의 미선택이며, 별도의 회피 클릭을 했다는 의미가 아니다.

Raw는 기존 필드를 재사용한다.

| type | observedValue | evidence |
| --- | --- | --- |
| RANKED_COMPARISON_PREFERENCE | `1`, `2` 등 순번 문자열 | EXPLICIT_RANKED_SELECTION |
| RANKED_COMPARISON_NON_PREFERENCE | NOT_PREFERRED | UNSELECTED_IN_RANKED_GROUP |

`optionId`는 `C02:B`와 같이 그룹 관계를 유지하고 definitionId, questionId,
answerRevision을 함께 보존한다. 순번은 점수가 아니다. 별도 DB 점수 규칙이 필요하지 않으며
기존 단일 비교용 관측 규칙이나 `EXPLICIT_SELECTION` 기반 후보 매핑에 넣지 않는다.

## 검증 결과

Java 21, 캐시된 Gradle 의존성으로 아래 관련 테스트 **93개 통과**, 실패/오류/건너뜀 0.
XML 보고서 14개로 확인했다.

```powershell
$env:JAVA_HOME='C:/Users/User/.jdks/temurin-21.0.11'
.\gradlew.bat --offline test --tests '*Ranked*Test' --tests '*ImageDefinitionTest' --tests '*ImageSessionStartTest' --tests '*PreferenceSummaryServiceTest' --tests '*TestSessionServiceTest' --tests '*TestSessionTest' --tests '*SingleChoice*Test' --tests '*MultiChoiceExclusiveOptionTest' --tests '*DirectObservationCollectorTest'
node --test dev/verification/assessment-state.test.mjs dev/verification/ranked-rendering.test.mjs
node --check src/main/resources/static/app.js
```

Node 테스트 **10개 통과**, 실패 0. app.js 문법 검사 통과.

| 요구 동작 | 검증 근거 |
| --- | --- |
| B → A 저장, 재조회, 수정 저장 | RankedAnswerPersistenceTest: 실제 서비스와 격리 Repository |
| 중간 해제·순위 당김·재선택 마지막 추가·복원 | assessment-state.test.mjs |
| 중복/미등록 ID·잘못된 필드·단일 비교에 배열 거부 | RankedComparisonTest 및 상태 테스트 |
| 최소 선택 수 명시, 0/전체 선택 설정 | RankedComparisonTest 및 상태 테스트 |
| 기존 단일 비교와 조건부 필수 검증 | ImageDefinitionTest, RankedComparisonTest |
| 일반 복수선택·단일 비교에 비선호 생성 안 함 | PreferenceSummaryServiceTest, RankedObservationTest |
| 순위형만 순위·비선호 보존 | PreferenceSummaryServiceTest, RankedObservationTest |
| 화면 배지·검토·결과·다운로드의 순서 | ranked-rendering.test.mjs: 실제 app.js 함수, DOM 대역 |
| 기존 남성복 정의·세션·요약 | ImageDefinitionTest, ImageSessionStartTest, 기존 서비스 회귀 테스트 |

실제 MySQL 저장·재조회와 브라우저 전체 사용자 흐름은 이번에 실행하지 않았다.
DB_USERNAME/DB_PASSWORD가 실행 환경에 없어 DB 테스트는 수행하지 않았다.
서비스 테스트는 Repository 대역을 사용하며 MySQL JSON 컬럼 통합 검증을 대체하지 않는다.
화면 테스트는 DOM 대역이므로 실제 브라우저 레이아웃·접근성 검증은 남아 있다.
전체 프로젝트 테스트를 실행했다는 의미도 아니다.

## 변경 파일 목록

수정:

- src/main/java/com/tastedna/session/application/comparison/ComparisonAnswerPayloadExtractor.java
- src/main/java/com/tastedna/session/application/comparison/ComparisonAnswerValidator.java
- src/main/java/com/tastedna/scoring/application/PreferenceSummaryService.java
- src/main/java/com/tastedna/scoring/application/SessionObservationService.java
- src/main/resources/static/assessment-state.mjs
- src/main/resources/static/app.js
- src/test/java/com/tastedna/scoring/PreferenceSummaryServiceTest.java
- dev/verification/assessment-state.test.mjs
- docs/storefront-integration.md
- dev/fixtures/README.md

추가:

- src/test/java/com/tastedna/session/RankedComparisonTest.java
- src/test/java/com/tastedna/session/RankedAnswerPersistenceTest.java
- src/test/java/com/tastedna/scoring/RankedObservationTest.java
- dev/verification/ranked-rendering.test.mjs
- dev/fixtures/simple/ranked_comparison_question_payload.json
- docs/ranked-comparison-contract.md

## 다음 여성복 구현 연결과 미완료 범위

1. 그룹별 최소 선택 수와 전체 여성복 질문 정책을 확정한다.
2. 승인된 새 정의의 해당 그룹에 selectionMode와 minSelections를 명시한다.
3. 서버가 승인된 새 정의를 선택하도록 별도 작업에서 시작 경로를 연결한다.
4. 기존 답변 저장/재조회/요약 API와 공통 렌더러를 그대로 사용한다.
5. 별도 테스트 DB와 실제 브라우저에서 전체 흐름을 검증한 뒤 활성화를 판단한다.

fixture는 자동 테스트 전용이며 런타임 정의 원본이 아니다. 활성화 또는 DB 등록하지 않았다.
여성복 Q08 C04/C05, Q09 C02와 남성복 Q09 C02는 적용 예정 위치일 뿐 이번에 수정하지 않았다.
여성복 전체 문항·시작 API, Deep, RESULT 최종 분류와 상품 추천은 범위 밖이다.

작업 시작 시 master이며 로컬 변경은 없었다. 작업 종료도 master,
위 수정 10개/신규 6개만 미커밋 상태다. 커밋·푸시·DB 변경은 수행하지 않았다.
