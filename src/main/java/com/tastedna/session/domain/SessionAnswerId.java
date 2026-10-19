package com.tastedna.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionAnswerId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(
            name = "session_id",
            columnDefinition = "CHAR(36)",
            nullable = false
    )
    private String sessionId;

    @Column(
            name = "question_id",
            length = 40,
            nullable = false
    )
    private String questionId;
}
