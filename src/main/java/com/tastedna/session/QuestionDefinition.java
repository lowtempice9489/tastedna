
package com.tastedna.session;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "td_question_definition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionDefinition {

    @EmbeddedId
    private QuestionDefinitionId id;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "question_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String questionPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "dependency_payload",
            columnDefinition = "json",
            nullable = false
    )
    private String dependencyPayload;

    @Column(name = "required", nullable = false)
    private boolean required;
}
