package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.ResultCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// RESULT 후보 정의를 DB에서 조회한다.
// 스타일 판별 자체는 수행하지 않는다.
public interface ResultCandidateRepository
        extends JpaRepository<ResultCandidate, String> {

    // 특정 그룹의 후보를 조회한다.
    List<ResultCandidate> findByCandidateGroupOrderByCandidateIdAsc(
            String candidateGroup
    );

    // 여러 후보 ID에 해당하는 정의를 한 번에 조회한다.
    List<ResultCandidate> findByCandidateIdIn(
            java.util.Collection<String> candidateIds
    );
}
