package com.tastedna.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Simple·Deep 테스트의 정의와 버전 정보를 보관하는 JPA 엔티티다.
// 세션은 생성 시 선택한 definitionId를 참조하므로 테스트 버전 구분의 기준이 된다.
@Getter
@Entity
@Table(name = "td_test_definition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestDefinition {

    @Id
    @Column(name = "definition_id", columnDefinition = "CHAR(36)")
    private String definitionId;

    @Column(name = "test_mode", nullable = false, length = 6)
    private String testMode;

    @Column(name = "question_count", nullable = false)
    private short questionCount;

    @Column(name = "active", nullable = false)
    private boolean active;

    // 테스트 정의 자체의 버전 식별자다.
    // 점수 규칙 및 Raw 관측 스키마 버전과 구분하여 관리한다.
    @Column(name = "version_code", nullable = false, length = 40)
    private String versionCode;

    // 테스트 정의가 참조하는 점수 규칙의 버전을 나타낸다.
    // 실제 점수 계산이나 RESULT 확정 로직을 이 엔티티가 수행하는 것은 아니다.
    @Column(name = "scoring_version", nullable = false, length = 40)
    private String scoringVersion;

    @Column(name = "raw_schema_version", nullable = false, length = 40)
    private String rawSchemaVersion;

    @Column(name = "equivalence_version", length = 40)
    private String equivalenceVersion;

}
