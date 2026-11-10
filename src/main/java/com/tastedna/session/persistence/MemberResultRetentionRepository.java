package com.tastedna.session.persistence;

import com.tastedna.session.domain.MemberResultRetention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface MemberResultRetentionRepository extends JpaRepository<MemberResultRetention, String> {
    Optional<MemberResultRetention> findBySessionIdAndMallMemberId(String sessionId, String memberId);
    Optional<MemberResultRetention> findByMallMemberIdAndCurrentMarker(String memberId, Integer marker);
    List<MemberResultRetention> findByMallMemberIdAndSavedSlotIsNotNullOrderBySavedAtDesc(String memberId);
    boolean existsBySessionIdAndMallMemberIdAndSavedSlotIsNotNull(String sessionId, String memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MemberResultRetention r where r.sessionId = :sessionId and r.mallMemberId = :memberId")
    Optional<MemberResultRetention> findForUpdate(@Param("sessionId") String sessionId, @Param("memberId") String memberId);
}
