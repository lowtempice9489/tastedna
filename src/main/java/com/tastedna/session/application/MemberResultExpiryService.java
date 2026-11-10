package com.tastedna.session.application;

import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.session.domain.MemberResultRetention;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.MemberResultRetentionRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// 만료는 보존 기간의 판정일 뿐 삭제 허가가 아니다. 관리 API와 같은 회원 잠금 아래 보호 상태를 재확인한다.
@Service
@RequiredArgsConstructor
public class MemberResultExpiryService {
    private final FormMemberRepository members;
    private final TestSessionRepository sessions;
    private final MemberResultRetentionRepository retention;

    public enum Status { NOT_SUBMITTED, LEGACY_EXEMPT, PROTECTED, TEMPORARY, EXPIRED }
    public record Review(String sessionId, Status status, LocalDateTime expiresAt) {}

    @Transactional
    public Review review(String member, UUID id) {
        lockMember(member);
        return reviewLocked(member, id.toString(), LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public List<Review> findExpired(String member, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("INVALID_REVIEW_LIMIT");
        lockMember(member);
        var now = LocalDateTime.now(ZoneOffset.UTC);
        return sessions.findTemporaryExpiryReviewIds(member, now, PageRequest.of(0, limit)).stream()
                .map(id -> reviewLocked(member, id, now)).filter(r -> r.status() == Status.EXPIRED).toList();
    }

    private Review reviewLocked(String member, String id, LocalDateTime now) {
        var session = sessions.findByIdAndMemberIdForUpdate(id, member)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"));
        var protection = retention.findForUpdate(id, member).orElse(null);
        return evaluate(session, protection, now);
    }

    static Review evaluate(TestSession session, MemberResultRetention protection, LocalDateTime now) {
        Status status;
        var expiry = session.getTemporaryResultExpiresAt();
        if (!"SUBMITTED".equals(session.getStatus()) || session.getSubmittedAt() == null) status = Status.NOT_SUBMITTED;
        else if (protection != null && protection.isProtected()) status = Status.PROTECTED;
        else if (expiry == null) status = Status.LEGACY_EXEMPT;
        else {
            // 적용 표식이 있어도 제출 시각보다 짧은 보존 기간을 허용하지 않는다.
            var minimum = session.getSubmittedAt().plusDays(5);
            if (expiry.isBefore(minimum)) expiry = minimum;
            status = now.isBefore(expiry) ? Status.TEMPORARY : Status.EXPIRED;
        }
        return new Review(session.getSessionId(), status, expiry);
    }

    private void lockMember(String member) {
        members.findByMemberIdForUpdate(member)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND"));
    }
}
