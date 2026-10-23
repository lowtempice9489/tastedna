package com.tastedna.session.persistence;

import com.tastedna.session.domain.TestSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

// 테스트 세션의 DB 조회와 저장을 담당하는 JPA Repository다.
// 회원별 세션 조회와 답변 수정 시 필요한 행 잠금 기능을 제공한다.
public interface TestSessionRepository
        extends JpaRepository<TestSession, String> {

    // 세션 ID와 회원 ID를 함께 조건으로 조회한다.
    // 다른 회원의 세션을 조회하지 않도록 서비스에서 사용하는 소유권 확인 경로다.
    Optional<TestSession> findBySessionIdAndMallMemberId(
            String sessionId,
            String mallMemberId
    );

    // 회원의 세션 중 지정한 상태에 해당하는 목록을 조회한다.
    // 새 세션 생성 시 활성 세션이 이미 존재하는지 확인하는 데 사용한다.
    List<TestSession> findByMallMemberIdAndStatusIn(
            String mallMemberId,
            List<String> statuses
    );

    // 답변 저장·세션 재개 중 동일 세션에 대한 동시 변경을 제어하기 위해 DB 행 잠금을 사용한다.
    // 세션 ID와 회원 ID를 함께 검사하며, 트랜잭션 종료 시 잠금이 해제된다.
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
