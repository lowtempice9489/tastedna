# TasteDNA 임시 쇼핑몰과 Simple 이미지 테스트

## 현재 제공하는 기능

FORM은 TasteDNA를 쇼핑몰의 추가 서비스로 소개하는 로컬 전시용 쇼핑몰이다.
상품 목록·카테고리 필터·상세 → TasteDNA 소개 → 회원 연결 → 10문항 →
답변 검토·제출 → 직접 선호 결과 → 쇼핑몰로 복귀 흐름을 제공한다.
상품명과 가격은 전시용 예시이며 주문·결제·재고 또는 개인화 추천 기능은 없다.

현재 구현 범위는 남성복 Simple이다. 여성복 이미지 원본은 보존하지만,
여성복 질문 규칙과 코드 대응이 승인되기 전에는 남성복 정의를 복제해 제공하지 않는다.

## 로컬 실행

1. Java 21과 MySQL 8.4를 준비한다.
2. IntelliJ 실행 구성의 working directory를 저장소 루트로 설정한다.
3. 환경변수 DB_USERNAME, DB_PASSWORD, DEV_MEMBER_PASSWORD를 설정한다.
   실제 값은 소스나 공유 문서에 기록하지 않는다.
4. local 프로필로 TastednaApplication을 실행한다.
5. http://localhost:8080 에서 전시 쇼핑몰을 연다.
6. TasteDNA에서 dev-image-demo와 설정한 개발용 비밀번호로 연결한다.
   기존 dev-member-001 / dev-member-002 계정은 이전 진행 세션 확인용으로 유지한다.

Gradle로 실행할 경우 JAVA_HOME을 Java 21로 지정한 뒤:

    .\gradlew.bat bootRun --args='--spring.profiles.active=local'

실행 시 Flyway가 V13~V15를 적용하므로 적용 대상 DB를 먼저 확인한다.
기존 활성 정의나 세션을 갱신·삭제하지 않고 새 정의 및 전시 데이터를 추가한다.
새 이미지 테스트는 서버가 고정된 정의 ID를 선택하는 전용 시작 API를 사용한다.
기존 정의의 active 상태를 바꾸지 않는다.

회원당 진행 중 세션은 하나라는 기존 정책을 유지한다. 진행 중 세션이 있으면
해당 세션을 이어간다. 이전 정의로 시작한 세션에는 새 이미지를 임의로 덧붙이지 않는다.
기존 Simple 제출 후 새 테스트를 시작하면 이미지 v2를 사용한다.
기존 Deep 세션이 있는 경우 이 화면에서 Simple로 변환하지 않는다.

## 이미지 제공

QuestionAssetConfig는 local 프로필에서 저장소의 gender 디렉터리를
/assets/gender/** 로 제공한다. HTML은 DB에서 내려온 이미지 경로만 사용한다.
따라서 배포 산출물의 JAR만 복사하면 이미지가 포함되지 않는다.
로컬 실행에는 gender 폴더가 함께 필요하다. 운영 도입 시 정적 파일 호스팅/CDN으로
교체하고 버전별 자산을 불변으로 보관해야 한다.

소스 이미지 자체는 변경하지 않았다. Q08 합본은 원본 비교 패널로 제공하고
선택 버튼을 연결했다. Q09 그룹의 여러 이미지는 하나의 선택 단위를 구성한다.
전체 이미지 확인은 크게 보기 버튼으로 제공한다.

## 질문 계약

새 정의: 33333333-3333-4333-8333-333333333333 / SIMPLE-MENS-IMAGE-V2

| 문항 | 구성 |
| --- | --- |
| Q01 | 텍스트 복수 선택, 최대 5 |
| Q02 | 텍스트 스타일 직접 선호, 최대 4 |
| Q03 | 텍스트 실루엣 선호, 최대 3 |
| Q04 | 텍스트 패턴 선호, 최대 4 |
| Q05 | A~F 착장 이미지 중 하나 |
| Q06 | A~G 의류 이미지 중 하나, 과거 P코드와 자동 동등시하지 않음 |
| Q07 | A~G 회피 이미지 최대 2개 또는 NONE 단독 |
| Q08 | C01 A/B 그룹, C02 A/B/C, 조건부 C02_POCKETS A/B, C03 A/B/C |
| Q09 | C01 A/B 그룹, C02 A~E 단일 선택, C03 A/B 그룹 |
| Q10 | A~J 착장 이미지 중 하나 |

Q08 C02에서 B 또는 C를 선택하면 C02_POCKETS가 필수다.
A로 바꾸면 후속 답변을 제거한다. 서버는 숨겨진 후속 답변도 거부한다.
비교 질문의 selections 배열은 comparisonId와 selectedOptionId를 함께 보존한다.
Q08 안내 문구는 3지선다·조건부 흐름에 맞춰 ‘작은 차이에서, 나의 취향을 골라주세요’로 제공한다.

## DB 우선 원칙

- V13: 질문·옵션·이미지 경로·조건·직접 관측 규칙을 기존 정의 테이블에 추가한다.
- V14: 실행 시 읽던 실험용 판별/시각 메타데이터 4개를 td_scoring_configuration으로 이전한다.
- V15: 전시 상품을 td_demo_product에 저장한다.
- 모든 새 DB 접근은 Spring Data JPA를 사용한다.
- 실행 코드에서 dev/fixtures/*.json 파일을 읽지 않는다.
- 기존 질문·답변 스냅샷 및 중첩 메타데이터는 DB JSON 컬럼을 유지한다.
  이는 파일 저장이 아니라 기존 계약과 비교/조건 관계를 보존하기 위한 DB 데이터다.
- dev/fixtures의 기존 JSON은 독립 알고리즘 테스트 및 이력 확인에 사용하는 자료다.
  운영 설정의 원본으로 다시 사용하지 않는다.

## API 연동

local 프로필에서 제공하는 추가 경로:

| 메서드 | 경로 | 역할 |
| --- | --- | --- |
| GET | /api/v1/demo/products | 공개 전시 상품 카탈로그 |
| POST | /api/v1/sessions/simple-image | 인증 회원의 남성복 이미지 v2 시작 |
| GET | /api/v1/sessions/{id}/answers | 회원 소유권 확인 후 session + 저장 답변 스냅샷 |
| GET | /api/v1/sessions/{id}/preference-summary | 제출된 Simple 세션의 근거 기반 요약 |

답변 저장과 제출은 기존 /answers POST 및 /submit POST를 사용한다.
정의 ID·회원 ID·채점 규칙은 클라이언트가 지정하지 않는다.
answers GET과 preference-summary는 세션 행 잠금 내에서 읽어 revision과 응답을 일치시킨다.
화면은 인증 정보를 브라우저 저장소에 저장하지 않는다. 새로고침 시 다시 연결하고
URL의 세션 ID로 저장된 답변을 복원한다. 답변은 다음 버튼을 눌렀을 때 DB에 저장된다.

기존 /simple-result는 기존 v1 후보 분석 계약을 유지한다.
이미지 v2에는 /preference-summary를 사용한다. 새 정의의 이미지/속성 의미를
기존 실험용 후보 매핑에 억지로 연결하지 않는다.

도입 쇼핑몰은 인증된 회원 식별자를 Spring Security Authentication에 연결하고,
데모 상품 API와 화면을 자체 카탈로그로 교체하면 된다.
실제 상품 추천은 승인된 상품 속성 매핑과 별도 정책이 필요하며 현재 구현에는 없다.

## 결과의 의미

결과는 Q02의 직접 선택한 스타일을 제목으로 사용하고 색상·핏·패턴,
첫인상 착장·소유 선호·최종 착장, 비교/조합 선택과 명시적 회피를 함께 보여준다.
25개 후보에 대한 검증되지 않은 점수·순위·성격 유형은 생성하지 않는다.
Q07 NONE은 회피 근거 없음이며 모든 착장을 좋아한다는 의미가 아니다.
기존 단일 비교에서 선택하지 않은 대안도 부정 선호로 분류하지 않는다.
명시적인 순위형 비교는 해당 그룹 안에서만 미선택 비선호를 보존한다.
공통 정의/답변 계약과 검증 범위는 [순위형 비교 인계](ranked-comparison-contract.md)를 참고한다.
결과는 저장된 presented_payload와 answer_revision에서 생성하므로,
현재 질문 표시 데이터를 변경해도 기존 답변의 설명을 바꾸지 않는다.
이미지 파일 자체를 덮어쓰는 것까지 막지는 않으므로 자산 버전 관리가 필요하다.

결과는 별도 결과 테이블에 저장하지 않고 제출 후 불변 답변에서 재구성한다.
요약 상태는 DIRECT_PREFERENCE_SUMMARY, comprehensiveResult는 false다.
DB의 기존 답변 데이터를 관리자가 직접 수정한 경우까지 불변성을 보장하지 않는다.

## 검증

DB 없이 실행 가능한 핵심 회귀 테스트:

    .\gradlew.bat test --offline --tests '*ImageDefinitionTest' --tests '*ImageSessionStartTest' --tests '*PreferenceSummaryServiceTest' --tests '*TestSessionServiceTest' --tests '*TestSessionTest' --tests '*SingleChoice*Test' --tests '*MultiChoiceExclusiveOptionTest' --tests '*DirectObservationCollectorTest'
    node --test dev/verification/assessment-state.test.mjs
    node --check src/main/resources/static/app.js
    node --check src/main/resources/static/shop.mjs

ImageDefinitionTest는 별도 JSON 파일이 아닌 실제 V13 마이그레이션에서 질문을 읽고
이미지 존재, 선택 개수, 조건부 응답, 기존 A/B 계약 호환성을 검증한다.
PreferenceSummaryServiceTest는 소유권, 제출 조건, 직접 회피, 비교 대안 및 근거 revision을 검증한다.
단위 테스트 통과는 MySQL 마이그레이션과 브라우저 전체 흐름의 통과를 뜻하지 않는다.

실제 검증 시 별도의 테스트 DB와 테스트 회원 데이터를 사용한다.
기존 개발 회원의 진행 중 세션을 테스트 편의상 변경하거나 삭제하지 않는다.


## 2026-11-03 선택 입구 추가

TasteDNA 시작 전 남성·여성 선택 화면을 제공한다. 남성은 기존 로그인·남성복 테스트로 연결된다. 여성은 준비 중 안내 및 선택 화면 복귀만 제공하며 API 호출이나 세션 생성은 하지 않는다. 결과의 새 기록 시작도 선택 화면으로 연결한다. 여성복은 선택지 수가 더 많고 일부 질문 수정이 필요하므로 실제 여성복 질문 정의·DB·결과 구현은 후속 작업이다. 상세 인수인계는 TasteDNA-ChatGPT-handoff-2026-11-03.md를 참고한다.
