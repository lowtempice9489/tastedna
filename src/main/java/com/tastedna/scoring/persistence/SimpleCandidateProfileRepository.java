package com.tastedna.scoring.persistence;

import com.tastedna.scoring.domain.SimpleCandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 테스트 정의에 속한 Simple RESULT 후보 프로필을 조회한다.
// 후보 프로필의 존재 여부를 검증된 판별 규칙으로 해석하지 않는다.
public interface SimpleCandidateProfileRepository
        extends JpaRepository<
        SimpleCandidateProfile,
        SimpleCandidateProfile.ProfileId> {

    List<SimpleCandidateProfile>
    findByIdDefinitionIdOrderByIdCandidateIdAsc(
            String definitionId
    );
}
