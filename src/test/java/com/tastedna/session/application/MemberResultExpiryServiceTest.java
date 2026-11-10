package com.tastedna.session.application;

import com.tastedna.member.domain.FormMember;
import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.session.domain.MemberResultRetention;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.MemberResultRetentionRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.tastedna.session.application.MemberResultExpiryService.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberResultExpiryServiceTest {
    private static final LocalDateTime SUBMITTED = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final FormMemberRepository members = mock(FormMemberRepository.class);
    private final TestSessionRepository sessions = mock(TestSessionRepository.class);
    private final MemberResultRetentionRepository retention = mock(MemberResultRetentionRepository.class);
    private final MemberResultExpiryService service = new MemberResultExpiryService(members, sessions, retention);

    private TestSession submitted() {
        var s = new TestSession(UUID.randomUUID().toString(), "member", "33333333-3333-4333-8333-333333333333",
                "MENS", "[]", SUBMITTED, SUBMITTED);
        s.submit(SUBMITTED);
        return s;
    }

    @Test void fiveDaysUsesSubmissionTimeAndExactBoundary() {
        var s = submitted();
        assertEquals(SUBMITTED.plusDays(5), s.getTemporaryResultExpiresAt());
        assertEquals(TEMPORARY, MemberResultExpiryService.evaluate(s, null, SUBMITTED.plusDays(5).minusNanos(1)).status());
        assertEquals(EXPIRED, MemberResultExpiryService.evaluate(s, null, SUBMITTED.plusDays(5)).status());
    }

    @Test void savedAndCurrentIndependentlyProtectAndLastReleaseDoesNotDeleteOrRestartClock() {
        var s = submitted();
        var row = new MemberResultRetention("member", s, "v1", "{}", SUBMITTED);
        row.saveToSlot(1, SUBMITTED);
        assertEquals(PROTECTED, MemberResultExpiryService.evaluate(s, row, SUBMITTED.plusDays(9)).status());
        row.setCurrent(SUBMITTED);
        row.removeSaved();
        assertEquals(PROTECTED, MemberResultExpiryService.evaluate(s, row, SUBMITTED.plusDays(9)).status());
        row.saveToSlot(2, SUBMITTED.plusDays(9));
        row.clearCurrent();
        assertEquals(PROTECTED, MemberResultExpiryService.evaluate(s, row, SUBMITTED.plusDays(9)).status());
        row.removeSaved();
        assertEquals(EXPIRED, MemberResultExpiryService.evaluate(s, row, SUBMITTED.plusDays(9)).status());
        assertEquals(SUBMITTED, s.getSubmittedAt());
        assertEquals(SUBMITTED.plusDays(5), s.getTemporaryResultExpiresAt());
        assertEquals("{}", row.getResultSnapshot());
        verifyNoInteractions(sessions, retention);
    }

    @Test void legacyAndInProgressAndGuestNeverAcquireRetroactiveExpiry() {
        var legacy = submitted();
        ReflectionTestUtils.setField(legacy, "temporaryResultExpiresAt", null);
        assertEquals(LEGACY_EXEMPT, MemberResultExpiryService.evaluate(legacy, null, SUBMITTED.plusYears(1)).status());
        var progress = new TestSession(UUID.randomUUID().toString(), "member", legacy.getDefinitionId(), "MENS", "[]", SUBMITTED, SUBMITTED);
        assertEquals(NOT_SUBMITTED, MemberResultExpiryService.evaluate(progress, null, SUBMITTED.plusYears(1)).status());
        var guest = new TestSession(UUID.randomUUID().toString(), null, legacy.getDefinitionId(), "MENS", "[]", SUBMITTED, SUBMITTED);
        guest.submit(SUBMITTED);
        assertNull(guest.getTemporaryResultExpiresAt());
    }

    @Test void candidateScanRechecksProtectionUnderSameMemberLockAsManagement() {
        var protectedSession = submitted();
        var expired = submitted();
        var row = new MemberResultRetention("member", protectedSession, "v1", "{}", SUBMITTED);
        row.setCurrent(SUBMITTED);
        when(members.findByMemberIdForUpdate("member")).thenReturn(Optional.of(mock(FormMember.class)));
        when(sessions.findTemporaryExpiryReviewIds(eq("member"), any(), any())).thenReturn(List.of(protectedSession.getSessionId(), expired.getSessionId()));
        when(sessions.findByIdAndMemberIdForUpdate(protectedSession.getSessionId(), "member")).thenReturn(Optional.of(protectedSession));
        when(sessions.findByIdAndMemberIdForUpdate(expired.getSessionId(), "member")).thenReturn(Optional.of(expired));
        when(retention.findForUpdate(protectedSession.getSessionId(), "member")).thenReturn(Optional.of(row));
        var found = service.findExpired("member", 10);
        assertEquals(List.of(expired.getSessionId()), found.stream().map(MemberResultExpiryService.Review::sessionId).toList());
        var order = inOrder(members, sessions, retention);
        order.verify(members).findByMemberIdForUpdate("member");
        order.verify(sessions).findTemporaryExpiryReviewIds(eq("member"), any(), any());
        order.verify(sessions).findByIdAndMemberIdForUpdate(protectedSession.getSessionId(), "member");
        order.verify(retention).findForUpdate(protectedSession.getSessionId(), "member");
        order.verify(sessions).findByIdAndMemberIdForUpdate(expired.getSessionId(), "member");
        order.verify(retention).findForUpdate(expired.getSessionId(), "member");
        verifyNoMoreInteractions(members, sessions, retention);
    }

    @Test void anotherMembersSessionCannotBeReviewed() {
        when(members.findByMemberIdForUpdate("member")).thenReturn(Optional.of(mock(FormMember.class)));
        var failure = assertThrows(ResponseStatusException.class, () -> service.review("member", UUID.randomUUID()));
        assertEquals(404, failure.getStatusCode().value());
        verifyNoInteractions(retention);
    }
}
