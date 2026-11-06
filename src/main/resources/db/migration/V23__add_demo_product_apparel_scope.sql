-- 상품 범위는 명시적 등록만 허용한다. 기존 이미지 폴더로 범위를 추정하지 않는다.
ALTER TABLE td_demo_product
    ADD COLUMN apparel_scope VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT chk_demo_product_apparel_scope
        CHECK (apparel_scope IN ('MENS', 'WOMENS', 'UNISEX'));

-- 기존 100개 상품은 승인된 범위 데이터가 없으므로 NULL(미분류)을 유지한다.
