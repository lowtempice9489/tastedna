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
        var submittedAt = LocalDateTime.now()
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
        when(members.findByMemberIdForUpdate("member"))
                .thenReturn(Optional.of(mock(FormMember.class)));

        when(sessions.findByIdAndMemberIdForUpdate(
                session.getSessionId(), "member"))
                .thenReturn(Optional.of(session));
    }

    @Test
    void expiredUnprotectedResultDeletesAnswersAndSession() {
        var session = submitted(91);
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
    void resultBeforeNinetyDaysMustNotBeDeleted() {
        var session = submitted(30);
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
}
