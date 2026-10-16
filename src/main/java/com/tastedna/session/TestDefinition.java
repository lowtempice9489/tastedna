
package com.tastedna.session;

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
}
