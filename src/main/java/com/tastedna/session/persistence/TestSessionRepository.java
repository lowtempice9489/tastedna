package com.tastedna.session.persistence;

import com.tastedna.session.domain.TestSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TestSessionRepository
        extends JpaRepository<TestSession, String> {

    Optional<TestSession> findBySessionIdAndMallMemberId(
            String sessionId,
            String mallMemberId
    );

    List<TestSession> findByMallMemberIdAndStatusIn(
            String mallMemberId,
            List<String> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from TestSession s
            where s.sessionId = :sessionId
              and s.mallMemberId = :memberId
            """)
    Optional<TestSession> findByIdAndMemberIdForUpdate(
            @Param("sessionId") String sessionId,
            @Param("memberId") String memberId
    );
}
