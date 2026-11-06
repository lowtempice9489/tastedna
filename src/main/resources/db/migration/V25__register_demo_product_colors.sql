-- 명시된 데모 상품명 색상만 Q01 선택지 어휘와 직접 연결한다. 이미지·유사 색상 추론은 하지 않는다.
-- 배색의 나머지 색상은 미상이며, 이 데이터는 추천 필터나 점수로 사용하지 않는다.
CREATE TABLE td_demo_product_color (
 product_id VARCHAR(40) NOT NULL,
 color_code VARCHAR(1) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY (product_id, color_code),
 CONSTRAINT fk_demo_product_color_product FOREIGN KEY (product_id) REFERENCES td_demo_product(product_id),
 CONSTRAINT chk_demo_product_color_code CHECK (color_code IN ('A','B','C','D','E','F','G','H','I','J','K','L','M','N','O'))
);
-- look-01: 블랙 집업 블루종 (V15__create_demo_store_catalog.sql) → Q01 A
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-01','A');
-- look-02: 그레이 셔츠형 재킷 (V15__create_demo_store_catalog.sql) → Q01 C
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-02','C');
-- look-03: 네이비 바시티 재킷 (V15__create_demo_store_catalog.sql) → Q01 F
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-03','F');
-- look-04: 브라운 트러커 재킷 (V15__create_demo_store_catalog.sql) → Q01 E
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-04','E');
-- look-05: 블루 데님 재킷 (V15__create_demo_store_catalog.sql) → Q01 G
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-05','G');
-- look-06: 그레이 후드 집업 (V15__create_demo_store_catalog.sql) → Q01 C
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-06','C');
-- look-07: 아이보리 텍스처 재킷 (V15__create_demo_store_catalog.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-07','B');
-- look-10: 핑크 버튼 가디건 (V21__expand_demo_catalog_to_30.sql) → Q01 J
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-10','J');
-- look-11: 베이지 더블버튼 재킷 (V21__expand_demo_catalog_to_30.sql) → Q01 D
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-11','D');
-- look-12: 브라운 벨트 셔츠 원피스 (V21__expand_demo_catalog_to_30.sql) → Q01 E
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-12','E');
-- look-14: 버건디 플레어 원피스 (V21__expand_demo_catalog_to_30.sql) → Q01 I
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-14','I');
-- look-15: 네이비 버튼 가디건 (V21__expand_demo_catalog_to_30.sql) → Q01 F
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-15','F');
-- look-16: 아이보리 칼라 셔츠 (V21__expand_demo_catalog_to_30.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-16','B');
-- look-17: 그레이 그래픽 후드 (V21__expand_demo_catalog_to_30.sql) → Q01 C
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-17','C');
-- look-18: 핑크 리본 블라우스 (V21__expand_demo_catalog_to_30.sql) → Q01 J
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-18','J');
-- look-19: 베이지 포켓 가디건 (V21__expand_demo_catalog_to_30.sql) → Q01 D
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-19','D');
-- look-20: 브라운 포켓 재킷 (V21__expand_demo_catalog_to_30.sql) → Q01 E
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-20','E');
-- look-21: 블랙 하이넥 니트 (V21__expand_demo_catalog_to_30.sql) → Q01 A
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-21','A');
-- look-24: 아이보리 브이넥 니트 (V21__expand_demo_catalog_to_30.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-24','B');
-- look-27: 네이비 배색 브이넥 니트 (V21__expand_demo_catalog_to_30.sql) → Q01 F
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-27','F');
-- look-28: 그레이 하프집업 상의 (V21__expand_demo_catalog_to_30.sql) → Q01 C
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-28','C');
-- look-29: 올리브 포켓 재킷 (V21__expand_demo_catalog_to_30.sql) → Q01 H
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-29','H');
-- look-30: 블랙 후드 집업 (V21__expand_demo_catalog_to_30.sql) → Q01 A
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-30','A');
-- look-34: 핑크 랩 가디건 (V22__expand_demo_catalog_to_100.sql) → Q01 J
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-34','J');
-- look-35: 아이보리 포켓 쇼트 재킷 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-35','B');
-- look-36: 블랙 집업 쇼트 재킷 (V22__expand_demo_catalog_to_100.sql) → Q01 A
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-36','A');
-- look-38: 네이비 트랙 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 F
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-38','F');
-- look-39: 네이비 배색 니트 베스트 (V22__expand_demo_catalog_to_100.sql) → Q01 F
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-39','F');
-- look-40: 블루 데님 쇼츠 (V22__expand_demo_catalog_to_100.sql) → Q01 G
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-40','G');
-- look-42: 올리브 롱 스트레이트 스커트 (V22__expand_demo_catalog_to_100.sql) → Q01 H
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-42','H');
-- look-43: 올리브 티어드 스커트 (V22__expand_demo_catalog_to_100.sql) → Q01 H
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-43','H');
-- look-44: 블루 버튼 데님 재킷 (V22__expand_demo_catalog_to_100.sql) → Q01 G
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-44','G');
-- look-45: 베이지 와이드 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 D
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-45','D');
-- look-46: 브라운 플레어 롱 스커트 (V22__expand_demo_catalog_to_100.sql) → Q01 E
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-46','E');
-- look-50: 그레이 라운드 스웨트셔츠 (V22__expand_demo_catalog_to_100.sql) → Q01 C
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-50','C');
-- look-54: 브라운 체크 집업 재킷 (V22__expand_demo_catalog_to_100.sql) → Q01 E
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-54','E');
-- look-55: 블루 칼라 집업 니트 (V22__expand_demo_catalog_to_100.sql) → Q01 G
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-55','G');
-- look-56: 블랙 카고 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 A
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-56','A');
-- look-57: 아이보리 와이드 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-57','B');
-- look-59: 네이비 싱글 블레이저 (V22__expand_demo_catalog_to_100.sql) → Q01 F
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-59','F');
-- look-60: 버건디 슬림 미니 원피스 (V22__expand_demo_catalog_to_100.sql) → Q01 I
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-60','I');
-- look-63: 핑크 프릴 티어드 스커트 (V22__expand_demo_catalog_to_100.sql) → Q01 J
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-63','J');
-- look-64: 네이비 스트레이트 정장 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 F
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-64','F');
-- look-65: 블랙 비대칭 지퍼 재킷 (V22__expand_demo_catalog_to_100.sql) → Q01 A
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-65','A');
-- look-66: 브라운 패턴 롱 원피스 (V22__expand_demo_catalog_to_100.sql) → Q01 E
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-66','E');
-- look-67: 그레이 조거 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 C
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-67','C');
-- look-68: 블루 스트레이트 데님 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 G
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-68','G');
-- look-69: 그레이 워싱 와이드 데님 (V22__expand_demo_catalog_to_100.sql) → Q01 C
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-69','C');
-- look-70: 아이보리 배색 트랙 집업 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-70','B');
-- look-71: 블랙 와이드 슬랙스 (V22__expand_demo_catalog_to_100.sql) → Q01 A
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-71','A');
-- look-72: 브라운 싱글 재킷 (V22__expand_demo_catalog_to_100.sql) → Q01 E
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-72','E');
-- look-73: 크림 배색 브이넥 니트 (V22__expand_demo_catalog_to_100.sql) → Q01 D
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-73','D');
-- look-74: 아이보리 턱 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-74','B');
-- look-75: 올리브 카고 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 H
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-75','H');
-- look-76: 블랙 배색 트랙 집업 (V22__expand_demo_catalog_to_100.sql) → Q01 A
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-76','A');
-- look-77: 퍼플 모티프 가디건 (V22__expand_demo_catalog_to_100.sql) → Q01 K
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-77','K');
-- look-78: 아이보리 무지 긴소매 티셔츠 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-78','B');
-- look-79: 아이보리 레터링 긴소매 티셔츠 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-79','B');
-- look-80: 아이보리 심볼 긴소매 티셔츠 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-80','B');
-- look-87: 아이보리 무장식 블라우스 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-87','B');
-- look-88: 아이보리 리본 포인트 블라우스 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-88','B');
-- look-89: 아이보리 프릴 블라우스 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-89','B');
-- look-90: 아이보리 레이스 리본 블라우스 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-90','B');
-- look-91: 베이지 스트레이트 슬랙스 (V22__expand_demo_catalog_to_100.sql) → Q01 D
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-91','D');
-- look-92: 블루 베이직 칼라 셔츠 (V22__expand_demo_catalog_to_100.sql) → Q01 G
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-92','G');
-- look-93: 블루 데님 스트레이트 팬츠 (V22__expand_demo_catalog_to_100.sql) → Q01 G
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-93','G');
-- look-95: 버건디 드레이프 상의 (V22__expand_demo_catalog_to_100.sql) → Q01 I
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-95','I');
-- look-96: 그레이 플리츠 미니 스커트 (V22__expand_demo_catalog_to_100.sql) → Q01 C
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-96','C');
-- look-97: 화이트 칼라 셔츠 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-97','B');
-- look-99: 아이보리 셔링 네크라인 블라우스 (V22__expand_demo_catalog_to_100.sql) → Q01 B
INSERT INTO td_demo_product_color (product_id,color_code) VALUES ('look-99','B');
