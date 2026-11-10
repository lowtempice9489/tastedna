package com.tastedna.session.persistence;

import com.tastedna.session.domain.MemberResultRetention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberResultRetentionRepository extends JpaRepository<MemberResultRetention, String> {
    Optional<MemberResultRetention> findBySessionIdAndMallMemberId(String sessionId, String memberId);
    Optional<MemberResultRetention> findByMallMemberIdAndCurrentMarker(String memberId, Integer marker);
    List<MemberResultRetention> findByMallMemberIdAndSavedSlotIsNotNullOrderBySavedAtDesc(String memberId);
    boolean existsBySessionIdAndMallMemberIdAndSavedSlotIsNotNull(String sessionId, String memberId);
}
