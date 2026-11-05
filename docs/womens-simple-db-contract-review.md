# 여성복 Simple DB 구현 전 계약 점검 — 09-D-1

## 13-A: 공통 Q01~Q04 부분 등록

V17은 초안의 `6acc799d-1348-4f34-b756-593e9f5bfd52` / `SIMPLE-WOMENS-IMAGE-V1`을 재사용한다.
남성복 V13 정의의 Q01~Q04 질문 JSON·의존 JSON·필수 여부·직접 관측을 INSERT SELECT로 복사하며
기존 남성복 행은 변경하지 않는다. 부모 정의는 비활성이고 예정 문항 수는 스키마 계약대로 10이다.
실제 등록 문항은 4개이며 완성된 Simple 정의를 뜻하지 않는다.
이미지 및 일반 세션 시작 경로 모두 여성복 Simple을 422로 거부한다.

이 부분 등록은 이번 명시적 승인에 따른 예외이며, 아래 초안의 전체 10문항 배포 금지는 그대로 유지한다.
Q05~Q10은 문구·선택 규칙·관측 계약 확정 후 같은 정의 ID에 새 마이그레이션으로 추가한다.
V17은 수정하지 않으며, 10문항 완성·검증 및 시작 연결 승인 전에는 활성화하지 않는다.

검증: `WomensSharedDefinitionTest`, `ImageSessionStartTest`는 DB 없이 실행한다.
`WomensSharedDefinitionMysqlTest`는 `RUN_WOMENS_MYSQL_TESTS=true`, `DB_USERNAME=coql`,
로컬에서 안전하게 설정한 `DB_PASSWORD`가 있어야 실행된다. 접속 URL은 localhost:3306/tastedna_test로 고정하고
Flyway 실행 전에 DB명·접속 계정을 검사한다. 기존 데이터 초기화나 삭제는 하지 않는다.
13-A 최초 작성 시에는 접속 환경변수가 없어 실제 DB 적용·JPA 검증을 실행하지 못했다.
이후 사용자가 IntelliJ PowerShell에서 실행한 테스트 보고서(2026-11-06 03:30 KST)를 확인했다.
`WomensSharedDefinitionMysqlTest`는 1개 실행, 실패·오류·스킵 0개로 통과했다.
테스트 DB·계정 사전 확인, Flyway 현재 버전 17, 비활성 여성복 정의의 Q01~Q04 및 직접 관측 계약의
남성복과의 일치, 두 시작 경로의 422 거부와 세션 수 불변을 검증했다. 작업 13-A 검증 완료.

## 점검 기준

시작 시 Git 작업 트리는 깨끗했다. 이번에 확인 가능한 메시지에는 「TasteDNA 선택 방식 정책 v1.0」의 첨부 본문·경로가 없으며, 저장소에서도 해당 문서를 찾지 못했다. 따라서 요청에 따라 기존 여성복 fixture의 계약을 기준으로 정리했다. 정책 v1.0 원문과 대조 완료한 문서가 아니다.

근거:

- [Q05 fixture](../dev/fixtures/simple/womens_simple_q05_draft.json), [Q06 fixture](../dev/fixtures/simple/womens_simple_q06_draft.json), [Q07 fixture](../dev/fixtures/simple/womens_simple_q07_draft.json)
- [Q08 fixture](../dev/fixtures/simple/womens_simple_q08_draft.json), [Q09 fixture](../dev/fixtures/simple/womens_simple_q09_draft.json), [Q10 fixture](../dev/fixtures/simple/womens_simple_q10_draft.json)
- [남성복 V13](../src/main/resources/db/migration/V13__add_mens_image_simple_definition.sql), [기존 인수인계서 7절](TasteDNA-ChatGPT-handoff-2026-11-03.md), [공통 순위형 계약](ranked-comparison-contract.md)

## 후속 사용자 승인 — Q01~Q04 공통 계약

### 09-D-5 통합 시 추가 확인

「TasteDNA 선택 방식 정책 v1.0.docx」(작성 기준 2026-11-03)를 실제로 읽었다. 위의 원문 미확인 기록은 09-D-1 당시 상태다. 이번 요청은 Q01~Q04의 설명·직접 관측 계약 공유까지 승인하며, Q09 C01·C03·C04의 A/B 그룹 단일선택도 확정한다. 문서의 해당 미확정 기록보다 이번 명시적 요청을 우선한다. 문서의 남성복 Q09 변경은 이번 작업 범위가 아니다.

기존 `dev/fixtures/simple/womens_simple_q05_q06_q10_definition_draft.sql` 하나를 Q01~Q10으로 확장했다. 별도 여성복 ID·버전, 문항별 확정 JSON, Q01~Q04 직접 관측을 `SET` 구문으로 준비한다. INSERT는 미확정 표식을 포함한 주석 템플릿이다. 실행 가능한 준비 구문과 완성된 DB 정의는 다르며, 이번에 DB에 적용하지 않는다. 기존 fixture는 근거 보존을 위해 수정하지 않았다.

남은 결정은 Q05~Q10의 최종 문구·옵션/그룹 표시명·관측 코드, 최소 선택·필수 여부·NONE, Q08/Q09 조건부 노출 및 순위형 최대값이다. Q07의 회피 관측 목적은 확정됐지만 구체적인 관측 코드와 NONE 규칙까지 승인된 것은 아니다. 정책 v1.0은 순위형에서 전체 선택 허용 여부도 미확정으로 명시하므로, 기존 검증기의 전체 선택 지원을 제품 승인으로 간주하지 않는다. 부모 정의의 관측 관련 버전·게시 시점도 초안 치환 지점으로 남긴다.

사용자의 「TasteDNA 여성복 Simple Q01~Q04 공통 계약 확정」 지시에 따라 남성복 V13의 질문 문구, 선택지 ID·표시명, 선택 방식과 최대 선택 개수를 여성복에도 동일하게 사용한다. 색상·패션 스타일·핏·실루엣·패턴의 기본 취향을 의류 범위에 따라 임의로 다르게 정의하지 않는다. 이 승인은 기존 인수인계서의 해당 공유 여부 미확정 기록보다 우선한다.

Q05 이후에는 실제 남성복·여성복 이미지와 승인된 질문 계약에 따라 분리한다. 여성복은 별도 테스트 정의·버전을 사용하며 기존 남성복 세션·답변은 보존한다.

Simple 10문항은 넓은 취향 방향 파악, Deep 25문항은 추가 관측을 통한 세밀한 차이 구별이 목표다. 이는 설계 목표이며 검증되지 않은 정확도·점수·확률로 표현하지 않는다. 이번 승인이 Deep 구현이나 RESULT 규칙 승인을 의미하지는 않는다.

## 그대로 반영할 수 있는 확정 필드

아래는 DB 정의를 작성할 때 재사용 가능한 **일부 필드**다. Q05~Q10은 모두 `DRAFT_NOT_FOR_ACTIVATION`이며, 현재 자료만으로 완성된 10문항 정의를 생성·활성화할 수는 없다. `designEvidence`의 이미지 설명은 코드 대응의 근거이며 승인된 관측 의미나 최종 표시문구가 아니다.

| 질문 ID | 반영 가능한 기존 계약 | 근거 |
| --- | --- | --- |
| Q01 | 남성복 질문 문구·옵션 표시명 공유. 일반 다중선택, A~O, 최대 5개 | V13 및 후속 사용자 승인 |
| Q02 | 남성복 질문 문구·옵션 표시명 공유. 일반 다중선택, R01~R12, 최대 4개 | V13 및 후속 사용자 승인 |
| Q03 | 남성복 질문 문구·옵션 표시명 공유. 일반 다중선택, S01~S06, 최대 3개 | V13 및 후속 사용자 승인 |
| Q04 | 남성복 질문 문구·옵션 표시명 공유. 일반 다중선택, P01~P09, 최대 4개 | V13 및 후속 사용자 승인 |
| Q05 | 일반 다중선택, A~J, 최대 3개, 개별 이미지 경로 | Q05 `questionPayload` |
| Q06 | 일반 다중선택, A~H, 최대 3개, 개별 이미지 경로 | Q06 `questionPayload` |
| Q07 | 일반 다중선택, A~K, 최대 3개, 개별 이미지 경로 | Q07 `questionPayload` |
| Q08 C01~C03 | 단일선택. C01=A/B(각 3장 묶음), C02=A~D, C03=A/B. 합본·개별 이미지 경로 | Q08 `comparisons` |
| Q08 C04·C05 | 순위형. C04=A~C, C05=A~D. 합본·개별 이미지 경로 | Q08 `comparisons` |
| Q09 C01·C03·C04 | 단일선택, 각각 A/B(각 4장 묶음). 합본·개별 이미지 경로 | Q09 `comparisons` |
| Q09 C02 | 순위형, A~E. 합본·개별 이미지 경로 | Q09 `comparisons` |
| Q10 | 일반 다중선택, A~S, 최대 6개, 개별 이미지 경로. 전체 합본은 참고 자료 | Q10 `questionPayload`, `referenceImages` |

순위형은 배열 순서를 보존하고 해당 그룹의 미선택만 비선호로 식별한다. 일반 다중선택·단일 비교의 미선택에는 이를 적용하지 않는다. 공통 순위형 계약의 `minSelections: 1`은 예시이며 여성복 최종 최소값이 아니다.

## 사용자 결정이 필요한 계약

| 질문 ID | 필요한 결정 | 기존 근거 |
| --- | --- | --- |
| Q01~Q04 | 최소 선택 수·필수 여부·NONE 처리의 적용 계약 | 09-D-5에서 문구·설명·옵션·선택 방식·최대·직접 관측 공유는 승인됨. 최소·필수·NONE에 대한 별도 승인으로 확대 해석하지 않음 |
| Q05~Q07 | 문항별 질문·설명·옵션 표시명 확정 | 각 `unresolved.prompt`, `description`, `optionLabels`. 남성복 Q05/Q06은 단일선택이므로 문구 자동 복사 불가 |
| Q05~Q07 | 문항별 최소 선택 수와 필수 여부, NONE 도입 여부 및 도입 시 배타 선택 처리 | 각 `minimumSelectionsAndRequired`, `noneOptionAndExclusivity`. 남성복 Q07의 최대 2개·NONE 정책은 여성복 승인 근거가 아님 |
| Q08~Q09 | 질문·설명, 비교 그룹·옵션의 최종 표시문구 | 각 `prompt`, `description`, `finalDisplayLabels`. `designEvidence`는 이미지에 나타난 대응 근거 |
| Q08 C04 / Q09 C02 | 옵션별 의미 이름·최종 표시명 확정 | 각 `unlabeledOptionMeanings`; 해당 `optionMeaningsFromSource`는 null. 이미지 경로 대응은 준비됨 |
| Q08 C04·C05 / Q09 C02 | 각 순위형 그룹의 최소 선택 수, 0개 허용 여부 및 필수 응답 처리 | `minimumSelectionsAndRequired`; `minSelections` 미기재. 공통 계약은 0 이상 명시적 정수를 요구하며 기본값 없음 |
| Q08 C01~C03 / Q09 C01·C03·C04 | 단일선택 그룹의 필수 응답·미응답 허용 여부 | `minimumSelectionsAndRequired`. 선택 방식 자체는 SINGLE로 확정됨 |
| Q08~Q09 | 조건부 노출 유무. 조건이 있으면 부모 비교·선택 ID와 대상 그룹 확정 | 각 `conditions`. 현재 `showWhen` 없음은 무조건 노출 승인과 다름. 남성복 Q08의 C02_POCKETS 조건을 복사하지 않음 |
| Q08 C04·C05 / Q09 C02 | 순위형 안내 문구와 기존 합본의 단일선택 안내를 어떻게 일치시킬지 | 각 `rankedOverviewCopy`. 이미지 경로가 있다는 사실만으로 현재 안내가 확정된 것은 아님 |
| Q10 | 질문·설명·옵션 표시명, 최소 선택 수·필수 여부, NONE 및 배타 처리 | `prompt`, `description`, `finalDisplayLabels`, `minimumSelectionsAndRequired`, `noneOptionAndExclusivity`. 남성복 Q10 단일선택과 다름 |
| Q05~Q10 | 문항·옵션·비교 그룹별 관측 의미와 저장할 관측 근거의 승인 범위 | 모든 fixture의 `observationMeaning`. 이미지 설명이나 남성복 관측 유형을 여성복 규칙으로 간주하지 않음 |

검증은 위 표와 fixture의 `questionPayload`·`designEvidence`·`unresolved`, V13의 문항 구조를 대조하는 것으로 한정했다. fixture·질문 정의·DB·API·프론트엔드를 변경하지 않았으며 전체 테스트는 실행하지 않았다.
