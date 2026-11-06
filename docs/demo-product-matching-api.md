# 명시적 스타일 속성 기반 데모 상품 매칭

`GET /api/v1/sessions/{sessionId}/matched-demo-products`

본인의 제출 완료 SIMPLE 세션만 허용한다. 타인 세션은 404, 제출 전은 409,
SIMPLE이 아니거나 Q02 답변이 없으면 422다. 인증과 소유권 검사는 유지한다.

`sessionId`, `definitionId`, `revision`, `products` 및 상품의 기존 필드는 유지한다.
`matchType`은 `EXPLICIT_STYLE_ATTRIBUTE`로 변경한다. 근거의 기존 필드
`questionId`, `optionId`, `observationType`, `answerRevision`에 `styleCode`, `styleLabel`을 추가한다.

저장 당시 Q02 선택과 같은 정의·답변 revision의 DIRECT_FASHION_LOOK_PREFERENCE /
EXPLICIT_SELECTION Raw 관측을 대조한다. 코드와 표시명 모두 승인 어휘와 정확히 같아야 한다.
상품 category는 분류이며 스타일로 해석하지 않는다. imageUrl은 표시 전용이다.
하나 이상의 스타일이 정확히 일치하면 반환하고, 여러 일치 근거는 코드 순으로 중복 없이 반환한다.
상품 순서는 ID순이며 점수·유사도·최종 RESULT는 사용하지 않는다.

V20은 상품별 스타일 집합 td_demo_product_style(product_id, style_code)을 추가한다.
기존 상품 태그를 채우지 않으므로 현재 전시 상품은 매칭되지 않는다.

## 상품 등록 데이터

향후 100개 등록 시 상품별로 다음 정보를 준비한다. 아래는 입력 형태이며 등록 API는 아니다.
styleCodes는 상품 담당자가 확인한 코드만 입력하고, 미확정이면 빈 배열로 둔다.

```json
{
  "productId": "고유 상품 ID",
  "name": "상품명",
  "category": "상품 분류",
  "price": 10000,
  "imageUrl": "/assets/products/상품이미지.png",
  "description": "상품 설명",
  "displayOrder": 1,
  "styleCodes": []
}
```

승인 어휘: R01 미니멀, R02 모던, R03 클래식, R04 프레피, R05 캐주얼,
R06 스포티, R07 스트릿, R08 빈티지·레트로, R09 로맨틱, R10 보헤미안,
R11 에지·그런지, R12 글램·화려함. Q02 옵션 코드이며 최종 RESULT 판정이 아니다.

실제 상품별 코드 배정 및 MySQL V20 적용 검증은 별도 작업이다.
기존 화면의 '선택한 착장' 표현은 스타일 매칭에 맞는 문구 정비가 남아 있다.
