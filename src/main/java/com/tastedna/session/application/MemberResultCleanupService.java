package com.tastedna.session.application;

import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.session.domain.MemberResultRetention;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.MemberResultRetentionRepository;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberResultCleanupService {
    private final FormMemberRepository members;
    private final TestSessionRepository sessions;
    private final MemberResultRetentionRepository retention;
    private final SessionAnswerRepository answers;

    // 각 세션을 별도 트랜잭션으로 처리한다. 관리 API와 동일한 잠금 순서를 유지한다.
    @Transactional
    public boolean deleteExpired(String member, String sessionId) {
        if (members.findByMemberIdForUpdate(member).isEmpty()) return false;
        var found = sessions.findByIdAndMemberIdForUpdate(sessionId, member);
        if (found.isEmpty()) return false;
        var session = found.get();
        var protection = retention.findForUpdate(sessionId, member);
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (MemberResultExpiryService.evaluate(session, protection.orElse(null), now).status()
                != MemberResultExpiryService.Status.EXPIRED) return false;
        deleteContents(session, protection);
        return true;
    }

    @Transactional
    public void deleteOwned(String member, UUID id) {
        members.findByMemberIdForUpdate(member).filter(m -> "ACTIVE".equals(m.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));
        var session = sessions.findByIdAndMemberIdForUpdate(id.toString(), member)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"));
        if (!"SUBMITTED".equals(session.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NOT_SUBMITTED");
        var protection = retention.findForUpdate(id.toString(), member);
        if (protection.filter(r -> r.getCurrentMarker() != null).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CURRENT_TASTEDNA_MUST_BE_CLEARED");
        // 직접 삭제에는 과거 기록·저장 여부·5일 경계를 적용하지 않는다. 현재 설정만 먼저 해제해야 한다.
        deleteContents(session, protection);
    }

    private void deleteContents(TestSession session, Optional<MemberResultRetention> protection) {
        String sessionId = session.getSessionId();

        // 보호 재검사 후 자식부터 명시적으로 제거한다. 세션 CASCADE에 정리를 맡기지 않는다.
        protection.ifPresent(retention::delete);
        retention.flush();
        answers.deleteAll(answers.findByIdSessionIdOrderByIdQuestionIdAsc(sessionId));
        answers.flush();
        sessions.delete(session);
        sessions.flush();
    }
}
