package com.tastedna.session.application;

import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.scoring.application.SimpleInitialCandidateService;
import com.tastedna.session.domain.MemberResultRetention;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.MemberResultRetentionRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberResultService {
    private final FormMemberRepository members;
    private final TestSessionRepository sessions;
    private final MemberResultRetentionRepository retention;
    private final SimpleInitialCandidateService results;
    private final JsonMapper mapper;

    public record Entry(String sessionId, boolean saved, boolean current, LocalDateTime savedAt,
                        LocalDateTime currentSince, SimpleInitialCandidateService.Response result) {}
    public record Collection(Entry current, List<Entry> saved, int limit) {}

    @Transactional(readOnly = true)
    public Collection list(String member) {
        return new Collection(retention.findByMallMemberIdAndCurrentMarker(member, 1).map(this::entry).orElse(null),
                retention.findByMallMemberIdAndSavedSlotIsNotNullOrderBySavedAtDesc(member).stream().map(this::entry).toList(),
                MemberResultRetention.MAX_SAVED_RESULTS);
    }

    @Transactional(readOnly = true)
    public Entry get(String member, UUID id) {
        owned(member, id, false);
        return retention.findBySessionIdAndMallMemberId(id.toString(), member).map(this::entry)
                .orElseGet(() -> new Entry(id.toString(), false, false, null, null, null));
    }

    @Transactional(readOnly = true)
    public SimpleInitialCandidateService.Response readResult(String member, UUID id) {
        var session = owned(member, id, false);
        return retention.findBySessionIdAndMallMemberId(id.toString(), member)
                .map(row -> checkedSnapshot(row, session)).orElseGet(() -> results.find(member, id));
    }

    @Transactional
    public Entry save(String member, UUID id) {
        lockMember(member);
        var session = owned(member, id, true);
        var existing = retention.findBySessionIdAndMallMemberId(id.toString(), member);
        if (existing.isPresent() && existing.get().getSavedSlot() != null) return entry(existing.get());
        var used = retention.findByMallMemberIdAndSavedSlotIsNotNullOrderBySavedAtDesc(member).stream()
                .map(MemberResultRetention::getSavedSlot).toList();
        int slot = 1;
        while (used.contains(slot)) slot++;
        if (slot > MemberResultRetention.MAX_SAVED_RESULTS)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RESULT_STORAGE_FULL");
        var row = existing.orElseGet(() -> capture(member, id, session));
        row.saveToSlot(slot, now());
        return entry(retention.saveAndFlush(row));
    }

    @Transactional
    public Entry unsave(String member, UUID id) {
        lockMember(member);
        owned(member, id, true);
        var row = retention.findBySessionIdAndMallMemberId(id.toString(), member);
        row.ifPresent(MemberResultRetention::removeSaved);
        return row.map(this::entry).orElseGet(() -> new Entry(id.toString(), false, false, null, null, null));
    }

    @Transactional
    public Entry setCurrent(String member, UUID id) {
        lockMember(member);
        var session = owned(member, id, true);

        var existing = retention.findBySessionIdAndMallMemberId(
                id.toString(), member);

        var evaluation = existing
                .map(row -> checkedSnapshot(row, session))
                .orElseGet(() -> results.find(member, id));

        if (!evaluation.finalResult()
                || evaluation.decision() == null
                || !"FINAL".equals(evaluation.decision().status())
                || evaluation.decision().representativeCandidate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "CURRENT_TASTEDNA_REQUIRES_FINAL");
        }

        var target = existing.orElseGet(() -> capture(member, id, session));

        retention.findByMallMemberIdAndCurrentMarker(member, 1)
                .filter(row -> !row.getSessionId().equals(id.toString()))
                .ifPresent(row -> {
                    row.clearCurrent();
                    retention.flush();
                });

        target.setCurrent(now());
        return entry(retention.saveAndFlush(target));
    }

    @Transactional
    public void clearCurrent(String member) {
        lockMember(member);
        retention.findByMallMemberIdAndCurrentMarker(member, 1).ifPresent(MemberResultRetention::clearCurrent);
    }

    private void lockMember(String member) {
        members.findByMemberIdForUpdate(member).filter(m -> "ACTIVE".equals(m.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));
    }

    private TestSession owned(String member, UUID id, boolean lock) {
        var session = (lock ? sessions.findByIdAndMemberIdForUpdate(id.toString(), member)
                : sessions.findBySessionIdAndMallMemberId(id.toString(), member))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"));
        if (!"SUBMITTED".equals(session.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NOT_SUBMITTED");
        return session;
    }

    private MemberResultRetention capture(String member, UUID id, TestSession session) {
        var response = results.find(member, id);
        if (!id.equals(response.sessionId()) || !session.getDefinitionId().equals(response.definitionId().toString())
                || session.getRevision() != response.revision() || response.decision().ruleVersion() == null)
            throw new IllegalStateException("RESULT_SNAPSHOT_MISMATCH");
        return new MemberResultRetention(member, session, response.decision().ruleVersion(), mapper.writeValueAsString(response), now());
    }

    private SimpleInitialCandidateService.Response checkedSnapshot(MemberResultRetention row, TestSession session) {
        var response = mapper.readValue(row.getResultSnapshot(), SimpleInitialCandidateService.Response.class);
        if (!row.getSessionId().equals(response.sessionId().toString()) || session.getRevision() != response.revision()
                || row.getSubmissionRevision() != response.revision()
                || !session.getDefinitionId().equals(response.definitionId().toString())
                || !row.getRuleVersion().equals(response.decision().ruleVersion()))
            throw new IllegalStateException("RESULT_SNAPSHOT_MISMATCH");
        return response;
    }

    private Entry entry(MemberResultRetention row) {
        var snapshot = checkedSnapshot(row, owned(row.getMallMemberId(), UUID.fromString(row.getSessionId()), false));
        return new Entry(row.getSessionId(), row.getSavedSlot() != null, row.getCurrentMarker() != null,
                row.getSavedAt(), row.getCurrentSince(), snapshot);
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
