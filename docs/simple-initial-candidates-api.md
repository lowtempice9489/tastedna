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
