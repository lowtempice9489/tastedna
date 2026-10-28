package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.StyleCoreDirectMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Q02 직접 선호 매핑을 기존 Spring Data JPA 방식으로 조회한다.
// JDBC 직접 실행 없이 엔티티와 Repository 조회 메서드를 사용한다.
public interface StyleCoreDirectMappingRepository
        extends JpaRepository<
        StyleCoreDirectMapping,
        StyleCoreDirectMapping.MappingId> {

    // 기존 서비스가 사용하는 선택지·후보 매핑 자료형.
    record DirectMapping(
            String optionId,
            String candidateId
    ) {
    }

    // 복합키의 Definition ID와 질문 ID로 DB 매핑을 조회한다.
    List<StyleCoreDirectMapping>
    findByIdSourceDefinitionIdAndIdQuestionIdOrderByIdOptionIdAsc(
            String definitionId,
            String questionId
    );

    // 기존 서비스와 테스트의 호출 방식을 유지한다.
    default List<DirectMapping> findByDefinitionAndQuestion(
            String definitionId,
            String questionId
    ) {
        return findByIdSourceDefinitionIdAndIdQuestionIdOrderByIdOptionIdAsc(
                definitionId,
                questionId
        ).stream()
                .map(entity -> new DirectMapping(
                        entity.getId().getOptionId(),
                        entity.getCandidateId()
                ))
                .toList();
    }
}
