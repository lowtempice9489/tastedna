# 여성복 Simple DB 구현 전 계약 점검 — 09-D-1

## 점검 기준

시작 시 Git 작업 트리는 깨끗했다. 이번에 확인 가능한 메시지에는 「TasteDNA 선택 방식 정책 v1.0」의 첨부 본문·경로가 없으며, 저장소에서도 해당 문서를 찾지 못했다. 따라서 요청에 따라 기존 여성복 fixture의 계약을 기준으로 정리했다. 정책 v1.0 원문과 대조 완료한 문서가 아니다.

근거:

- [Q05 fixture](../dev/fixtures/simple/womens_simple_q05_draft.json), [Q06 fixture](../dev/fixtures/simple/womens_simple_q06_draft.json), [Q07 fixture](../dev/fixtures/simple/womens_simple_q07_draft.json)
- [Q08 fixture](../dev/fixtures/simple/womens_simple_q08_draft.json), [Q09 fixture](../dev/fixtures/simple/womens_simple_q09_draft.json), [Q10 fixture](../dev/fixtures/simple/womens_simple_q10_draft.json)
- [남성복 V13](../src/main/resources/db/migration/V13__add_mens_image_simple_definition.sql), [기존 인수인계서 7절](TasteDNA-ChatGPT-handoff-2026-11-03.md), [공통 순위형 계약](ranked-comparison-contract.md)

## 그대로 반영할 수 있는 확정 필드

아래는 DB 정의를 작성할 때 재사용 가능한 **일부 필드**다. Q05~Q10은 모두 `DRAFT_NOT_FOR_ACTIVATION`이며, 현재 자료만으로 완성된 10문항 정의를 생성·활성화할 수는 없다. `designEvidence`의 이미지 설명은 코드 대응의 근거이며 승인된 관측 의미나 최종 표시문구가 아니다.

| 질문 ID | 반영 가능한 기존 계약 | 근거 |
| --- | --- | --- |
| Q01 | 여성복 확정 계약 없음. 남성복 참고값: 일반 다중선택, A~O, 최대 5개 | V13 및 인수인계서 7절 |
| Q02 | 여성복 확정 계약 없음. 남성복 참고값: 일반 다중선택, R01~R12, 최대 4개 | V13 및 인수인계서 7절 |
| Q03 | 여성복 확정 계약 없음. 남성복 참고값: 일반 다중선택, S01~S06, 최대 3개 | V13 및 인수인계서 7절 |
| Q04 | 여성복 확정 계약 없음. 남성복 참고값: 일반 다중선택, P01~P09, 최대 4개 | V13 및 인수인계서 7절 |
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
| Q01~Q04 | 각 문항을 남성복과 공유할지. 공유 범위: 질문·설명·옵션 ID/표시명·선택 방식/최대 개수·필수/최소·NONE·관측 의미. 공유하지 않을 항목은 여성복 계약 필요 | 여성복 fixture 없음. 인수인계서 7절에 공유 여부 미확정. V13은 남성복 근거만 제공 |
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
