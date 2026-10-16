
package com.tastedna.session;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
