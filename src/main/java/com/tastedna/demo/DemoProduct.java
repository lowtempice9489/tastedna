package com.tastedna.demo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.util.HashSet;
import java.util.Set;

// TasteDNA를 소개하기 위한 전시 상품. 실제 쇼핑몰 연동 시 상품 시스템으로 교체한다.
@Entity
@Table(name = "td_demo_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemoProduct {
    public enum ApparelScope { MENS, WOMENS, UNISEX }

    // 이미지 경로나 상품명으로 추론하지 않는다. 미등록 상품은 매칭에서 제외한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "apparel_scope", length = 10)
    private ApparelScope apparelScope;

    @Id @Column(name = "product_id", length = 40)
    private String productId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 40)
    private String category;
    // 상품 분류와 분리된 명시적 스타일 태그이며 기존 상품에는 자동 부여하지 않는다.
    @ElementCollection
    @CollectionTable(name = "td_demo_product_style", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "style_code", nullable = false, length = 3)
    private Set<String> styleCodes = new HashSet<>();
    // 상품명에 명시된 색상만 Q01 코드로 등록한다. 미등록은 빈 집합이며 추천 조건에는 사용하지 않는다.
    @ElementCollection
    @CollectionTable(name = "td_demo_product_color", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "color_code", nullable = false, length = 1)
    private Set<String> colorCodes = new HashSet<>();
    @Column(nullable = false)
    private int price;
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;
    @Column(nullable = false, length = 1000)
    private String description;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

