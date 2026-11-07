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
Q01 색상 일치 근거가 있는 상품을 먼저 배치하고, 각 그룹 내부는 상품 ID순을 유지한다. 색상 일치 개수는 순위에 영향을 주지 않는다. Q01 미응답이면 기존 ID순이다. 점수·유사도·최종 RESULT는 사용하지 않는다.

V20은 상품별 스타일 집합 td_demo_product_style(product_id, style_code)을 추가한다.
V21~V22로 카탈로그가 총 100개로 확장되었으며 V23~V24 의류 범위, V25 색상 속성을 사용한다. 범위 미분류 또는 스타일 근거가 없는 상품은 반환하지 않는다.

## 상품 등록 데이터

외부 상품 연결 시 상품별로 다음 정보를 준비한다. 아래는 데이터 준비 형식이며 등록 API는 아니다.
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
  "styleCodes": [],
  "apparelScope": null,
  "colorCodes": []
}
```

승인 어휘: R01 미니멀, R02 모던, R03 클래식, R04 프레피, R05 캐주얼,
R06 스포티, R07 스트릿, R08 빈티지·레트로, R09 로맨틱, R10 보헤미안,
R11 에지·그런지, R12 글램·화려함. Q02 옵션 코드이며 최종 RESULT 판정이 아니다.



## 의류 범위와 색상 근거

MENS 세션은 MENS·UNISEX, WOMENS 세션은 WOMENS·UNISEX만 허용한다. 범위 미등록은 제외한다. 위 상품 예시의 null과 빈 배열은 미등록을 뜻한다.

Q01의 최신 저장 답변과 같은 정의·revision의 DIRECT_COLOR_PREFERENCE / EXPLICIT_SELECTION만 사용한다. 등록 colorCodes와 정확히 일치할 때 기존 evidence에 `colorCode`, `colorLabel`을 제공한다. Q01 근거의 styleCode/styleLabel은 null이고, Q02 근거의 colorCode/colorLabel은 null이다. 색상 불일치나 미등록은 기존 스타일 추천에서 제외하는 조건이 아니다.

응답은 `sessionId, definitionId, revision, matchType, products`이며 상품은 `id, name, imageUrl, price, demo, evidence`다. 화면은 응답 순서대로 최초 4개와 더 보기를 제공한다. 상품 색상·범위 등록 근거는 [색상 문서](demo-catalog-colors.md)와 [의류 범위 문서](demo-catalog-apparel-scope.md)에 있다. 색상 우선 정렬은 15-T3B3 Java 테스트로 검증했으며 변경 후 실제 DB·브라우저 순서 검증과는 구분한다.
