# 직접 선택 이미지와 데모 상품 연결

`GET /api/v1/sessions/{sessionId}/matched-demo-products`

local / member-auth에서 본인의 제출 완료 SIMPLE 세션만 조회한다.
회원 인증 방식은 그대로이며 신규 GET 경로만 기존 MEMBER 권한 목록에 등록했다.
다른 회원의 세션은 404, 제출 전은 409, SIMPLE이 아니거나 Q06 답변이 없으면 422다.

응답은 `sessionId`, `definitionId`, `revision`, `matchType: "EXACT_SELECTED_IMAGE"`, `products`다.
상품은 `id`, `name`, `imageUrl`, `price`, `demo: true`, `evidence`를 포함한다.
근거에는 `questionId`, `optionId`, `observationType`, `answerRevision`이 포함된다.
상품명과 가격은 V15의 시연용 데이터이며 실제 판매 조건이 아니다.

저장된 Q06 답변의 선택지와 같은 정의·답변 revision에서 수집한
`DIRECT_CLOTHING_PREFERENCE` / `EXPLICIT_SELECTION` 관측을 대조한다.
선택지의 저장 당시 이미지 경로와 데모 상품 imageUrl이 정확히 일치해야 한다.
DB의 대소문자 구분 설정과 무관하게 Java 문자열 동일성도 확인한다.
일치하는 상품이 없거나 긍정 관측이 아니면 빈 목록이다.
상품은 ID 순서로 반환하며 선호 순위·점수·유사도는 부여하지 않는다.

현재 V13 Q06 A~G와 V15 look-01~look-07의 7개 이미지가 연결 가능하다.
현재 남성복 Q06은 단일 선택이므로 정상 세션에서는 해당 상품 1개가 연결된다.
RESULT 후보나 판별 보류 여부는 사용하지 않는다. 여성복 정의는 활성화하지 않는다.
상품 조회는 기존 JPA DemoProductRepository를 확장했으며 별도 상품 시스템 추상화는 추가하지 않았다.

검증은 실제 V13·V15 파일을 읽는 Java 테스트 및 저장소 대역을 사용한 회원 MockMvc 테스트다.
실제 MySQL·브라우저 통합 검증은 수행하지 않는다.
