package com.tastedna.member.persistence;

import com.tastedna.member.domain.FormMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FormMemberRepository extends JpaRepository<FormMember, String> {
    Optional<FormMember> findByEmail(String email);
    boolean existsByEmail(String email);
}
