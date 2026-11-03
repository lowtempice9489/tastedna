package com.tastedna.scoring.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 기존의 중첩된 실험 메타데이터를 정의별 DB 문서로 보존한다.
// 검증 상태나 내용을 변경하지 않고 파일 의존성만 제거한다.
@Entity
@Table(name = "td_scoring_configuration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoringConfiguration {
    @Id @Column(name = "configuration_id", length = 160)
    private String configurationId;
    @Column(name = "definition_id", nullable = false, columnDefinition = "CHAR(36)")
    private String definitionId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;
}

