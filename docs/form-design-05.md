# 작업 05 — FORM 홈과 TasteDNA 표지

기준 HEAD: `43420b7677c9a14f88c42f218999a7194f736dcd`. 시작 작업 트리는 깨끗했다.
첨부 번호의 혼동은 사용자 확인을 받아 파일 내용 기준으로 적용했다.

- `form_취향으로_만나는_봄_스타일.png`: FORM 홈 기준.
- `form_tastedna_취향_쇼핑_경험.png`: TasteDNA 소개 기준.

## 구현 범위

FORM은 화이트·그레이지·차콜, 세리프 브랜드, 큰 프로모션과 두 보조 에디트,
중간 크기 TasteDNA 배너, 기존 데모 상품 목록으로 구성했다.
보조 에디트는 실제 상품의 재킷/텍스처 필터로 연결된다.
기존 고해상도 착장 이미지와 데모 상품 데이터를 재사용했다.

TasteDNA는 아이보리·웜그레이·토프·세이지, 큰 세리프 제목, 테스트 시작과 이용 안내,
네 단계 안내 카드로 구성했다. 특정 의류/성별 전용 인상을 줄이기 위해 인물 사진 대신
색상 견본·질감 카드·곡선 장식을 HTML/CSS로 표현했다. 시안 전체 이미지는 배경으로 사용하지 않았다.
첨부 인물/상품 사진을 그대로 복제한 구현은 아니다.

현재 제공 범위는 남성복 Simple과 직접 선호 요약임을 표기한다.
시안의 검색·찜·장바구니·다른 상품 영역·할인/인기 순위·맞춤 추천 등 미구현 기능은
작동하는 것처럼 표시하지 않았다. 기존 전시 상품의 예시 가격/판매 아님 안내도 유지한다.

## 변경 파일

- `src/main/resources/static/index.html`: 공통 FORM 헤더·회원 메뉴 배치·푸터·메타 설명.
- `src/main/resources/static/shop.mjs`: FORM 홈 레이아웃과 보조 에디트 필터 연결.
- `src/main/resources/static/app.js`: `home()` 소개 화면 마크업과 안내 스크롤만 변경.
- `src/main/resources/static/shop.css`: 화면별 스타일, 반응형, 글자 대비·줄바꿈.
- `dev/verification/form-flow.test.mjs`: 새 표지에서 기존 의류 선택/FORM 로그인으로 이어지는 회귀 검증.
- `docs/form-design-05.md`: 이 문서.

기존 app.css와 인증/선택/요약 구조는 재사용한다. app.js의 home() 외 코드는 HEAD와 동일함을
문자열 비교로 확인했다. 인증 모듈, CSRF, 서버 세션, Java, DB, Flyway, 질문/답변 계약은 수정하지 않았다.

## 검증

- Node **18개 통과, 실패 0**: 회원 인증 전송, CSRF, local Basic 분리, 로그인 복귀,
  남성복 10문항 정의·선택 상태, 순위형 표시·요약·다운로드, 신규 표지 진입 회귀.
- 실제 내장 브라우저의 읽기 전용 로컬 미리보기에서 두 화면의 1440/768/390/320px 폭을 확인했다.
  가로 넘침 없음, 데스크톱/모바일 스크린샷 육안 검사, 사용한 이미지 정상 로드를 확인했다.
- 재킷 프로모션 → 실제 재킷 필터(2개 상품), TasteDNA 배너 → 별도 소개,
  이용 안내 스크롤, 시작 → 의류 선택 → FORM 로그인 안내를 확인했다.
- 미리보기는 기존 V15 데모 상품값과 미로그인 상태를 응답하는 테스트 대역이다.
  DB와 연결하지 않았고 가입/저장 기능을 대신 구현하지 않았다. 미리보기 서버는 검증 후 종료했다.
- 사용자 요청에 따라 전체 MySQL·브라우저 가입~제출 종합 검증은 반복하지 않았다.
  Java 코드 변경이 없어 Java 테스트도 재실행하지 않았다. 다른 브라우저 엔진은 미검증이다.

```powershell
node --test dev/verification/assessment-state.test.mjs dev/verification/ranked-rendering.test.mjs dev/verification/form-auth.test.mjs dev/verification/form-flow.test.mjs
```

커밋·푸시는 하지 않았다.
