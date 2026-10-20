package com.tastedna.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "version_code", nullable = false, length = 40)
    private String versionCode;

    @Column(name = "scoring_version", nullable = false, length = 40)
    private String scoringVersion;

    @Column(name = "raw_schema_version", nullable = false, length = 40)
    private String rawSchemaVersion;

    @Column(name = "equivalence_version", length = 40)
    private String equivalenceVersion;

}
