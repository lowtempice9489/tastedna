package com.tastedna.session.application;

import com.tastedna.member.domain.FormMember;
import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.scoring.application.SimpleInitialCandidateService;
import com.tastedna.session.domain.MemberResultRetention;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.MemberResultRetentionRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberResultServiceTest {
    private final FormMemberRepository members = mock(FormMemberRepository.class);
    private final TestSessionRepository sessions = mock(TestSessionRepository.class);
    private final MemberResultRetentionRepository retention = mock(MemberResultRetentionRepository.class);
    private final SimpleInitialCandidateService results = mock(SimpleInitialCandidateService.class);
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final MemberResultService service = new MemberResultService(members, sessions, retention, results, mapper);
    private final Map<String, TestSession> submitted = new HashMap<>();
    private final Map<String, MemberResultRetention> rows = new LinkedHashMap<>();

    @BeforeEach void repositoryBoundaries() {
        when(members.findByMemberIdForUpdate("member")).thenReturn(Optional.of(
                new FormMember("member", "member@example.invalid", "unused", LocalDateTime.now())));
        when(sessions.findByIdAndMemberIdForUpdate(anyString(), anyString())).thenAnswer(i -> owned(i.getArgument(0), i.getArgument(1)));
        when(sessions.findBySessionIdAndMallMemberId(anyString(), anyString())).thenAnswer(i -> owned(i.getArgument(0), i.getArgument(1)));
        when(retention.findBySessionIdAndMallMemberId(anyString(), anyString())).thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(0)))
                .filter(r -> r.getMallMemberId().equals(i.getArgument(1))));
        when(retention.findByMallMemberIdAndSavedSlotIsNotNullOrderBySavedAtDesc(anyString())).thenAnswer(i -> rows.values().stream()
                .filter(r -> r.getMallMemberId().equals(i.getArgument(0)) && r.getSavedSlot() != null).toList());
        when(retention.findByMallMemberIdAndCurrentMarker(anyString(), eq(1))).thenAnswer(i -> rows.values().stream()
                .filter(r -> r.getMallMemberId().equals(i.getArgument(0)) && r.getCurrentMarker() != null).findFirst());
        when(retention.saveAndFlush(any(MemberResultRetention.class))).thenAnswer(i -> {
            MemberResultRetention r = i.getArgument(0); rows.put(r.getSessionId(), r); return r;
        });
    }

    private Optional<TestSession> owned(String id, String member) {
        return Optional.ofNullable(submitted.get(id)).filter(s -> member.equals(s.getMallMemberId()));
    }

    private UUID submit(boolean isFinal) {
        var now = LocalDateTime.now();
        UUID id = UUID.randomUUID();
        var s = new TestSession(id.toString(), "member", "33333333-3333-4333-8333-333333333333", "MENS", "[]", now, now);
        s.advanceRevision(0); s.submit(now); submitted.put(id.toString(), s);
        var candidate = new SimpleInitialCandidateService.Candidate("R01", "Minimal", List.of(),
                new SimpleInitialCandidateService.CandidateEvidence(List.of(), List.of(), List.of(), "test"));
        var response = new SimpleInitialCandidateService.Response(id, UUID.fromString(s.getDefinitionId()), 1,
                "INITIAL_CANDIDATES", isFinal, List.of(candidate), new SimpleInitialCandidateService.Decision(
                isFinal ? "FINAL" : "DEFERRED", isFinal ? List.of(candidate) : List.of(), List.of(), isFinal ? "R01" : null, "test-v1"), candidate);
        when(results.find("member", id)).thenReturn(response);
        return id;
    }

    @Test void fiveSlotsDuplicateAndSixthSaveRejectionDoNotPreventNewSubmissionOrChangeCurrent() {
        UUID first = submit(true);
        assertNull(service.list("member").current());
        assertTrue(service.list("member").saved().isEmpty());
        service.setCurrent("member", first);
        service.save("member", first);
        String snapshot = rows.get(first.toString()).getResultSnapshot();
        service.save("member", first);
        assertEquals(1, service.list("member").saved().size());
        for (int i = 0; i < 4; i++) service.save("member", submit(true));
        UUID sixth = submit(true);
        assertEquals(6, submitted.size());
        assertEquals(first.toString(), service.list("member").current().sessionId());
        assertEquals(5, service.list("member").saved().size());
        assertEquals("RESULT_STORAGE_FULL", assertThrows(ResponseStatusException.class, () -> service.save("member", sixth)).getReason());
        service.unsave("member", first);
        assertTrue(service.list("member").current().current());
        service.save("member", sixth);
        assertEquals(1, rows.get(sixth.toString()).getSavedSlot());
        assertEquals(snapshot, rows.get(first.toString()).getResultSnapshot());
    }

    @Test void currentReplacementUsesMemberLockAndFlushesOldMarkerBeforeNewAndKeepsSavedState() {
        UUID first = submit(true), next = submit(true);
        service.save("member", first);
        service.setCurrent("member", first);
        clearInvocations(members, sessions, retention);
        service.setCurrent("member", next);
        assertNull(rows.get(first.toString()).getCurrentMarker());
        assertNotNull(rows.get(first.toString()).getSavedSlot());
        assertNull(rows.get(next.toString()).getSavedSlot());
        assertEquals(1, rows.values().stream().filter(r -> r.getCurrentMarker() != null).count());
        var order = inOrder(members, sessions, retention);
        order.verify(members).findByMemberIdForUpdate("member");
        order.verify(sessions).findByIdAndMemberIdForUpdate(next.toString(), "member");
        order.verify(retention).flush();
        order.verify(retention).saveAndFlush(rows.get(next.toString()));
        service.save("member", next);
        service.clearCurrent("member");
        assertNull(service.list("member").current());
        assertEquals(2, service.list("member").saved().size());
    }

    @Test void deferredCanBeSavedButRejectedCurrentDoesNotMutateCurrentSlotsOrSnapshot() {
        UUID current = submit(true), deferred = submit(false);
        service.setCurrent("member", current);
        assertEquals("CURRENT_TASTEDNA_REQUIRES_FINAL", assertThrows(ResponseStatusException.class,
                () -> service.setCurrent("member", deferred)).getReason());
        assertFalse(rows.containsKey(deferred.toString()));
        service.save("member", deferred);
        var row = rows.get(deferred.toString());
        String snapshot = row.getResultSnapshot();
        var savedAt = row.getSavedAt();
        clearInvocations(retention);
        assertThrows(ResponseStatusException.class, () -> service.setCurrent("member", deferred));
        assertEquals(current.toString(), service.list("member").current().sessionId());
        assertEquals(1, row.getSavedSlot());
        assertEquals(savedAt, row.getSavedAt());
        assertEquals(snapshot, row.getResultSnapshot());
        verify(retention, never()).flush();
        verify(retention, never()).saveAndFlush(any());
    }

    @Test void legacyDeferredCurrentIsNotAutomaticallyReplacedOrReclassified() {
        UUID legacy = submit(false);
        service.save("member", legacy);
        rows.get(legacy.toString()).setCurrent(LocalDateTime.now());
        submit(true);
        assertEquals(legacy.toString(), service.list("member").current().sessionId());
        assertFalse(service.readResult("member", legacy).finalResult());
        service.unsave("member", legacy);
        assertEquals(legacy.toString(), service.list("member").current().sessionId());
    }

    @Test void otherMembersCannotSaveSetReadOrUnsaveAndNoSnapshotIsCreated() {
        UUID foreign = submit(true);
        when(sessions.findByIdAndMemberIdForUpdate(foreign.toString(), "member")).thenReturn(Optional.empty());
        when(sessions.findBySessionIdAndMallMemberId(foreign.toString(), "member")).thenReturn(Optional.empty());
        clearInvocations(results, retention);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.save("member", foreign)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.setCurrent("member", foreign));
        assertThrows(ResponseStatusException.class, () -> service.unsave("member", foreign));
        assertThrows(ResponseStatusException.class, () -> service.readResult("member", foreign));
        assertTrue(service.list("another-member").saved().isEmpty());
        verifyNoInteractions(results);
        verify(retention, never()).saveAndFlush(any());
    }
}
