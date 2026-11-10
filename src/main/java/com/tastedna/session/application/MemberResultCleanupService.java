package com.tastedna.session.application;

import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.session.persistence.MemberResultRetentionRepository;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
                != MemberResultExpiryService.Status.EXPIRED
                || session.getSubmittedAt().isAfter(now.minusDays(90))) return false;

        // 보호 재검사 후 자식부터 명시적으로 제거한다. 세션 CASCADE에 정리를 맡기지 않는다.
        protection.ifPresent(retention::delete);
        retention.flush();
        answers.deleteAll(answers.findByIdSessionIdOrderByIdQuestionIdAsc(sessionId));
        answers.flush();
        sessions.delete(session);
        sessions.flush();
        return true;
    }
}
