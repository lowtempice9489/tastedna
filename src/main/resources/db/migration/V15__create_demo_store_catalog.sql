-- 임시 쇼핑몰 전시 데이터. 이름과 가격은 시연용이며 실제 판매·추천 정보가 아니다.
CREATE TABLE td_demo_product (
 product_id VARCHAR(40) PRIMARY KEY,
 name VARCHAR(120) NOT NULL,
 category VARCHAR(40) NOT NULL,
 price INT NOT NULL,
 image_url VARCHAR(500) NOT NULL,
 description VARCHAR(1000) NOT NULL,
 display_order INT NOT NULL
);
INSERT INTO td_demo_product (product_id,name,category,price,image_url,description,display_order) VALUES ('look-01','블랙 집업 블루종','데일리',59000,'/assets/gender/male/Q06/TasteDNA_Q06_A.png','이미지의 전체적인 분위기를 살펴보고 나의 취향을 탐색해보세요. 제공된 착장 이미지를 활용한 전시용 샘플로, 상품명과 가격은 시연을 위한 예시입니다.',1);
INSERT INTO td_demo_product (product_id,name,category,price,image_url,description,display_order) VALUES ('look-02','그레이 셔츠형 재킷','텍스처',79000,'/assets/gender/male/Q06/TasteDNA_Q06_B.png','이미지의 전체적인 분위기를 살펴보고 나의 취향을 탐색해보세요. 제공된 착장 이미지를 활용한 전시용 샘플로, 상품명과 가격은 시연을 위한 예시입니다.',2);
INSERT INTO td_demo_product (product_id,name,category,price,image_url,description,display_order) VALUES ('look-03','네이비 바시티 재킷','재킷',89000,'/assets/gender/male/Q06/TasteDNA_Q06_C.png','이미지의 전체적인 분위기를 살펴보고 나의 취향을 탐색해보세요. 제공된 착장 이미지를 활용한 전시용 샘플로, 상품명과 가격은 시연을 위한 예시입니다.',3);
INSERT INTO td_demo_product (product_id,name,category,price,image_url,description,display_order) VALUES ('look-04','브라운 트러커 재킷','데일리',69000,'/assets/gender/male/Q06/TasteDNA_Q06_D.png','이미지의 전체적인 분위기를 살펴보고 나의 취향을 탐색해보세요. 제공된 착장 이미지를 활용한 전시용 샘플로, 상품명과 가격은 시연을 위한 예시입니다.',4);
INSERT INTO td_demo_product (product_id,name,category,price,image_url,description,display_order) VALUES ('look-05','블루 데님 재킷','재킷',99000,'/assets/gender/male/Q06/TasteDNA_Q06_E.png','이미지의 전체적인 분위기를 살펴보고 나의 취향을 탐색해보세요. 제공된 착장 이미지를 활용한 전시용 샘플로, 상품명과 가격은 시연을 위한 예시입니다.',5);
INSERT INTO td_demo_product (product_id,name,category,price,image_url,description,display_order) VALUES ('look-06','그레이 후드 집업','데일리',65000,'/assets/gender/male/Q06/TasteDNA_Q06_F.png','이미지의 전체적인 분위기를 살펴보고 나의 취향을 탐색해보세요. 제공된 착장 이미지를 활용한 전시용 샘플로, 상품명과 가격은 시연을 위한 예시입니다.',6);
INSERT INTO td_demo_product (product_id,name,category,price,image_url,description,display_order) VALUES ('look-07','아이보리 텍스처 재킷','텍스처',85000,'/assets/gender/male/Q06/TasteDNA_Q06_G.png','이미지의 전체적인 분위기를 살펴보고 나의 취향을 탐색해보세요. 제공된 착장 이미지를 활용한 전시용 샘플로, 상품명과 가격은 시연을 위한 예시입니다.',7);
