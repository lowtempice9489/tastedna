# Simple 초기 후보 조회

`GET /api/v1/sessions/{sessionId}/initial-candidates`

local / member-auth에서 제공한다. 인증된 본인의 제출 완료 SIMPLE 세션만 조회한다.
기존 `/simple-result` 실험 API 및 `/preference-summary` 계약은 유지한다.
member-auth에서는 기존 MEMBER 권한 목록에 이 GET 경로만 추가했으며 쿠키·CSRF·회원 상태 검사는 동일하다.

응답: `sessionId`, `definitionId`, `revision`, `status: "INITIAL_CANDIDATES"`,
`finalResult: false`, `candidates` 배열.
후보는 `candidateId`, 카탈로그의 `candidateName`, `sources`를 가진다.
근거는 `questionId`, 저장된 질문의 `questionLabel`, `optionId`, `optionLabel`,
`observationType`, `evidenceType`, `answerRevision`을 포함한다.

V9의 Q02 R01~R12 직접 매핑을 재사용한다. V13 이미지 Q02와 기존 Q02 fixture의
선택지 ID·표시명이 일치하고, 직접 관측값은 선택지 표시명인 것을 확인했다.
실행 시 저장된 선택지 표시명과 관측값도 공통 이미지 Q02 계약과 대조한다.
Q02의 `DIRECT_FASHION_LOOK_PREFERENCE` / `EXPLICIT_SELECTION`만 사용한다.
등록된 STYLE_CORE 후보와 연결되는 근거만 반환하며 없는 후보를 채우지 않는다.
여성복도 동일한 승인 계약과 관측을 제공하면 같은 경로를 재사용할 수 있다. 여성복 정의를 활성화하지 않는다.

현재 Q02 최대 선택 수는 4이므로 실제 후보는 최대 4개다. API 상한은 5개다.
후보 ID 오름차순은 재현 가능한 표시 순서이며 선호 순위가 아니다.
향후 계약에서 5개를 넘으면 임의로 상위 후보를 선정하지 않고
422 `CANDIDATE_LIMIT_POLICY_REQUIRED`로 거부한다.
본인 세션이 아니면 404, 제출 전이면 409, SIMPLE이 아니거나 Q02 답변이 없으면 422다.

이는 최종 RESULT 분류가 아니다. 추가 관측의 활용, 후보 간 판별,
최종 분류 기준과 검증은 별도 승인이 필요하다. 점수·가중치·확률·추천은 제공하지 않는다.
검증은 저장 답변→관측→후보 경로의 Java 테스트와 회원 필터 MockMvc 테스트로 제한하며,
실제 MySQL 및 브라우저 검증은 포함하지 않는다.

## 후보별 관측 근거 (11-B)

기존 필드를 유지하고 후보마다 `evidence`를 추가한다.

- `positive`: 해당 후보의 확인된 Q02 직접 선택 근거. 기존 `sources`와 같은 자료이며 두 목록을 합산하지 않는다.
- `avoidanceOrConflict`: 승인된 후보별 회피·충돌 관계. 현재 승인된 연결이 없어 빈 배열이다.
- `unresolved`: 해당 후보와의 관계가 미확정인 세션 관측. 다른 Q02 후보의 선택도 자동 충돌로 해석하지 않는다.
- `status`: `CANDIDATE_RELATIONS_PENDING`.

미확정 관측은 `definitionId`, `questionId`, `optionId`, `comparisonId`,
`observationType`, `observedValue`, `evidenceType`, `answerRevision`, `rank`를 보존한다.
비교 선택지 ID는 원래 `C02:B` 형태를 유지한다. 순위는 해당 그룹 안에서 숫자 오름차순으로 표시한다.
회피 사실은 `DIRECT_OUTFIT_AVOIDANCE`, 회피 없음은 `NO_EXPLICIT_OUTFIT_AVOIDANCE`,
순위형의 미선택은 `RANKED_COMPARISON_NON_PREFERENCE`로 구별한다.
이 사실만으로 특정 후보와의 충돌은 확정되지 않는다. 일반 미선택의 부정 관측은 생성하지 않는다.
같은 관측은 각 후보 목록 안에서 중복 제거한다. 후보 간 공유 관측도 합산용 점수가 아니다.

현재 저장소의 V10 후보 속성 매핑은 EXPERIMENTAL, V11 후보 프로필은 PROPOSED이며
이미지 정의와도 별개다. 이를 승인된 지지·충돌로 사용하지 않는다.
최종 판별에는 정의별 이미지·색상·핏·패턴·비교·회피의 후보 연결 승인,
공유 근거와 후보 구별 근거의 구분, 순위·충돌의 해석 및 최종 결정 규칙의 검증이 필요하다.
