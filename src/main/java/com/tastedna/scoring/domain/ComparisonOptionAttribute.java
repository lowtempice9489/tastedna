package com.tastedna.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Q08 비교 선택지의 이미지와 속성값을 DB에서 조회한다.
// 이미지 자체의 검증이나 RESULT 선정은 여기서 수행하지 않는다.
@Getter
@Entity
@Table(name = "td_comparison_option_attribute")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComparisonOptionAttribute {

    @EmbeddedId
    private ComparisonOptionAttributeId id;

    // 실제 비교 이미지 식별자. 아직 정해지지 않았다면 null이다.
    @Column(name = "visual_asset_id", length = 100)
    private String visualAssetId;

    // 비교하는 속성의 종류다.
    @Column(name = "attribute_key", length = 80)
    private String attributeKey;

    // 해당 선택지 이미지가 갖는 속성값이다.
    @Column(name = "attribute_value", length = 120)
    private String attributeValue;

    // 판별에 필요한 기본 속성값이 존재하는지 확인한다.
    // 이 조건만으로 실제 이미지가 검증됐다는 뜻은 아니다.
    public boolean hasAttributeDefinition() {
        return visualAssetId != null
                && !visualAssetId.isBlank()
                && attributeKey != null
                && !attributeKey.isBlank()
                && attributeValue != null
                && !attributeValue.isBlank();
    }
}
