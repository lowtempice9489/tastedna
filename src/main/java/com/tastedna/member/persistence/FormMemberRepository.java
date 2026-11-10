package com.tastedna.member.persistence;

import com.tastedna.member.domain.FormMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface FormMemberRepository extends JpaRepository<FormMember, String> {
    Optional<FormMember> findByEmail(String email);
    boolean existsByEmail(String email);

    // 보관 행이 아직 없는 회원도 잠글 수 있어야 저장 슬롯 할당과 현재 설정 교체를 직렬화할 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from FormMember m where m.memberId = :memberId")
    Optional<FormMember> findByMemberIdForUpdate(@Param("memberId") String memberId);
}
