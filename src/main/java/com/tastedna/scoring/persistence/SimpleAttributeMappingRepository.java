package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.SimpleAttributeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Simple RESULT 후보별 속성 근거를 Spring Data JPA로 조회한다.
// EXPERIMENTAL 근거를 VERIFIED 근거로 자동 승격하지 않는다.
public interface SimpleAttributeMappingRepository
        extends JpaRepository<
        SimpleAttributeMapping,
        SimpleAttributeMapping.MappingId> {

    // 특정 테스트 정의의 속성 매핑을 후보·질문·선택지 순서로 읽는다.
    List<SimpleAttributeMapping>
    findByIdDefinitionIdOrderByIdCandidateIdAscIdQuestionIdAscIdOptionIdAsc(
            String definitionId
    );
}
