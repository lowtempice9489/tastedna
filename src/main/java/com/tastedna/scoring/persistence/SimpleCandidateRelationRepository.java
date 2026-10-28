package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.SimpleCandidateRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Simple RESULT 후보의 공유·양립 속성 관계를 DB에서 조회한다.
// 관계 유형을 변경하거나 최종 스타일 판별을 수행하지 않는다.
public interface SimpleCandidateRelationRepository
        extends JpaRepository<
        SimpleCandidateRelation,
        SimpleCandidateRelation.RelationId> {

    // 테스트 정의별 관계를 후보·질문·선택지 순서로 조회한다.
    List<SimpleCandidateRelation>
    findByIdDefinitionIdOrderByIdCandidateIdAscIdQuestionIdAscIdOptionIdAsc(
            String definitionId
    );
}
