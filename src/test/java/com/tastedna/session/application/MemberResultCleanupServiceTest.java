package com.tastedna.session.application;

import com.tastedna.member.domain.FormMember;
import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.session.domain.MemberResultRetention;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.MemberResultRetentionRepository;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberResultCleanupServiceTest {

    private final FormMemberRepository members =
            mock(FormMemberRepository.class);
    private final TestSessionRepository sessions =
            mock(TestSessionRepository.class);
    private final MemberResultRetentionRepository retention =
            mock(MemberResultRetentionRepository.class);
    private final SessionAnswerRepository answers =
            mock(SessionAnswerRepository.class);

    private final MemberResultCleanupService service =
            new MemberResultCleanupService(
                    members, sessions, retention, answers);

    private TestSession submitted(int daysAgo) {
        var submittedAt = LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(daysAgo)
                .minusHours(1);

        var session = new TestSession(
                UUID.randomUUID().toString(),
                "member",
                "33333333-3333-4333-8333-333333333333",
                "MENS",
                "[]",
                submittedAt,
                submittedAt
        );
        session.submit(submittedAt);
        return session;
    }

    private void prepare(TestSession session) {
        var member = mock(FormMember.class);
        when(member.getStatus()).thenReturn("ACTIVE");
        when(members.findByMemberIdForUpdate("member")).thenReturn(Optional.of(member));

        when(sessions.findByIdAndMemberIdForUpdate(
                session.getSessionId(), "member"))
                .thenReturn(Optional.of(session));
    }

    @Test
    void expiredUnprotectedResultDeletesAnswersAndSession() {
        var session = submitted(6);
        prepare(session);

        when(retention.findForUpdate(
                session.getSessionId(), "member"))
                .thenReturn(Optional.empty());

        assertTrue(service.deleteExpired(
                "member", session.getSessionId()));

        var order = inOrder(members, sessions, retention, answers);
        order.verify(members).findByMemberIdForUpdate("member");
        order.verify(sessions).findByIdAndMemberIdForUpdate(
                session.getSessionId(), "member");
        order.verify(retention).findForUpdate(
                session.getSessionId(), "member");
        order.verify(answers).deleteAll(anyList());
        order.verify(answers).flush();
        order.verify(sessions).delete(session);
        order.verify(sessions).flush();
    }

    @Test
    void savedResultMustNotBeDeleted() {
        var session = submitted(100);
        prepare(session);

        var row = new MemberResultRetention(
                "member", session, "test-v1", "{}",
                session.getSubmittedAt());
        row.saveToSlot(1, session.getSubmittedAt());

        when(retention.findForUpdate(
                session.getSessionId(), "member"))
                .thenReturn(Optional.of(row));

        assertFalse(service.deleteExpired(
                "member", session.getSessionId()));

        verifyNoInteractions(answers);
        verify(sessions, never()).delete(any());
        verify(retention, never()).delete(any());
    }

    @Test
    void currentResultMustNotBeDeleted() {
        var session = submitted(100);
        prepare(session);

        var row = new MemberResultRetention(
                "member", session, "test-v1", "{}",
                session.getSubmittedAt());
        row.setCurrent(session.getSubmittedAt());

        when(retention.findForUpdate(
                session.getSessionId(), "member"))
                .thenReturn(Optional.of(row));

        assertFalse(service.deleteExpired(
                "member", session.getSessionId()));

        verifyNoInteractions(answers);
        verify(sessions, never()).delete(any());
    }

    @Test
    void resultBeforeFiveDaysMustNotBeDeleted() {
        var session = submitted(4);
        prepare(session);

        assertFalse(service.deleteExpired(
                "member", session.getSessionId()));

        verifyNoInteractions(answers);
        verify(sessions, never()).delete(any());
    }

    @Test
    void legacyResultMustNotBeDeleted() {
        var session = submitted(120);
        ReflectionTestUtils.setField(
                session, "temporaryResultExpiresAt", null);
        prepare(session);

        assertFalse(service.deleteExpired(
                "member", session.getSessionId()));

        verifyNoInteractions(answers);
        verify(sessions, never()).delete(any());
    }

    @Test
    void missingMemberMustNotDeleteAnything() {
        when(members.findByMemberIdForUpdate("member"))
                .thenReturn(Optional.empty());

        assertFalse(service.deleteExpired(
                "member", UUID.randomUUID().toString()));

        verifyNoInteractions(sessions, retention, answers);
    }

    @Test
    void missingSessionMustNotDeleteAnything() {
        when(members.findByMemberIdForUpdate("member"))
                .thenReturn(Optional.of(mock(FormMember.class)));

        assertFalse(service.deleteExpired(
                "member", UUID.randomUUID().toString()));

        verifyNoInteractions(retention, answers);
    }
    @Test
    void explicitDeleteRemovesSavedLegacyResultInChildFirstOrder() {
        var session = submitted(120);
        ReflectionTestUtils.setField(session, "temporaryResultExpiresAt", null);
        prepare(session);
        var row = new MemberResultRetention("member", session, "test-v1", "{}", session.getSubmittedAt());
        row.saveToSlot(3, session.getSubmittedAt());
        when(retention.findForUpdate(session.getSessionId(), "member")).thenReturn(Optional.of(row));
        service.deleteOwned("member", UUID.fromString(session.getSessionId()));
        var order = inOrder(members, sessions, retention, answers);
        order.verify(members).findByMemberIdForUpdate("member");
        order.verify(sessions).findByIdAndMemberIdForUpdate(session.getSessionId(), "member");
        order.verify(retention).findForUpdate(session.getSessionId(), "member");
        order.verify(retention).delete(row);
        order.verify(retention).flush();
        order.verify(answers).deleteAll(anyList());
        order.verify(answers).flush();
        order.verify(sessions).delete(session);
    }

    @Test
    void explicitDeleteRejectsCurrentAndInProgressWithoutRemovingAnything() {
        var session = submitted(1);
        prepare(session);
        var row = new MemberResultRetention("member", session, "test-v1", "{}", session.getSubmittedAt());
        row.setCurrent(session.getSubmittedAt());
        when(retention.findForUpdate(session.getSessionId(), "member")).thenReturn(Optional.of(row));
        assertEquals("CURRENT_TASTEDNA_MUST_BE_CLEARED", assertThrows(ResponseStatusException.class,
                () -> service.deleteOwned("member", UUID.fromString(session.getSessionId()))).getReason());
        ReflectionTestUtils.setField(session, "status", "IN_PROGRESS");
        assertEquals("SESSION_NOT_SUBMITTED", assertThrows(ResponseStatusException.class,
                () -> service.deleteOwned("member", UUID.fromString(session.getSessionId()))).getReason());
        verifyNoInteractions(answers);
        verify(retention, never()).delete(any());
        verify(sessions, never()).delete(any());
    }

    @Test
    void explicitDeleteCannotAccessAnotherMembersSession() {
        var session = submitted(1);
        prepare(session);
        when(sessions.findByIdAndMemberIdForUpdate(session.getSessionId(), "member")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.deleteOwned("member", UUID.fromString(session.getSessionId()))).getStatusCode().value());
        verifyNoInteractions(retention, answers);
        verify(sessions, never()).delete(any());
    }

    @Test
    void childDeletionFailurePropagatesBeforeSessionDeletion() {
        var session = submitted(6);
        prepare(session);
        doThrow(new IllegalStateException("child failure")).when(answers).flush();
        assertThrows(IllegalStateException.class, () -> service.deleteExpired("member", session.getSessionId()));
        verify(sessions, never()).delete(any());
    }

}
