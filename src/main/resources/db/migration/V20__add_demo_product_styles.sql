-- Q02 직접 스타일 선택 어휘만 허용한다. 기존 전시 상품의 스타일은 추론하여 채우지 않는다.
CREATE TABLE td_demo_product_style (
    product_id VARCHAR(40) NOT NULL,
    style_code VARCHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (product_id, style_code),
    CONSTRAINT fk_demo_product_style_product FOREIGN KEY (product_id) REFERENCES td_demo_product(product_id),
    CONSTRAINT chk_demo_product_style_code CHECK (style_code IN
        ('R01','R02','R03','R04','R05','R06','R07','R08','R09','R10','R11','R12')),
    INDEX ix_demo_product_style_code (style_code, product_id)
);
