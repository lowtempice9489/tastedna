package com.tastedna.session;

import com.tastedna.scoring.api.SimpleResultController;
import com.tastedna.scoring.application.Q08ComparisonEvidenceService;
import com.tastedna.scoring.application.StyleCoreCandidateService;
import com.tastedna.session.application.TestSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

// RESULT 컨트롤러는 local 프로필에서만 활성화된다.
// 테스트 트랜잭션은 종료 시 롤백해 기존 세션을 보호한다.
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class TestSessionServiceIntegrationTest {

    @Autowired
    private TestSessionService sessionService;

    @Autowired
    private StyleCoreCandidateService candidateService;

    @Autowired
    private Q08ComparisonEvidenceService q08EvidenceService;

    @Autowired
    private SimpleResultController simpleResultController;

    // 서비스가 Spring Bean으로 정상 등록되었는지 확인한다.
    // 실제 세션 제출은 후속 테스트에서 검증한다.
    @Test
    void loadsSessionService() {
        assertNotNull(sessionService);
    }

    // 실제 Simple 세션의 정상 제출을 검증한다.
// @Transactional 테스트 종료 시 제출 상태 변경은 롤백된다.
    @Test
    void submitsCompletedSimpleSessionAndRollsBack() {
        String memberId = "dev-member-002";

        java.util.UUID sessionId = java.util.UUID.fromString(
                "b656c1a6-8013-49de-8e1a-29203e9800c1"
        );

        // 기존 세션 상태를 확인하고 현재 Revision을 사용한다.
        var before = sessionService.get(memberId, sessionId);

        assertEquals("SIMPLE", before.mode().name());
        assertEquals("IN_PROGRESS", before.status());

        var submitted = sessionService.submitSimple(
                memberId,
                sessionId,
                before.revision()
        );

        assertEquals("SUBMITTED", submitted.status());
        assertEquals(before.revision(), submitted.revision());

        // 같은 트랜잭션에서 제출 상태가 조회되는지 확인한다.
        var after = sessionService.get(memberId, sessionId);

        assertEquals("SUBMITTED", after.status());
    }

    // 이미 제출한 세션은 다시 제출할 수 없다.
// 테스트 종료 시 최초 제출 상태 변경도 롤백된다.
    @Test
    void rejectsDuplicateSimpleSubmission() {
        String memberId = "dev-member-002";

        java.util.UUID sessionId = java.util.UUID.fromString(
                "b656c1a6-8013-49de-8e1a-29203e9800c1"
        );

        var before = sessionService.get(memberId, sessionId);

        assertEquals("IN_PROGRESS", before.status());

        // 첫 번째 제출은 성공한다.
        var submitted = sessionService.submitSimple(
                memberId,
                sessionId,
                before.revision()
        );

        assertEquals("SUBMITTED", submitted.status());

        // 동일한 세션의 두 번째 제출은 거부되어야 한다.
        var exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> sessionService.submitSimple(
                        memberId,
                        sessionId,
                        before.revision()
                )
        );

        assertEquals(
                org.springframework.http.HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "SESSION_NOT_SUBMITTABLE",
                exception.getReason()
        );
    }

    // 제출된 Simple 세션에서도 저장된 응답으로 RESULT를 조회한다.
// 제출은 테스트 응답의 확정이며, 종합 스타일 판별 확정과는 구분한다.
    @Test
    void buildsSimpleResultAfterSubmission() {
        String memberId = "dev-member-002";

        java.util.UUID sessionId = java.util.UUID.fromString(
                "b656c1a6-8013-49de-8e1a-29203e9800c1"
        );

        var before = sessionService.get(memberId, sessionId);

        assertEquals("IN_PROGRESS", before.status());

        // 같은 트랜잭션에서 테스트를 제출한다.
        var submitted = sessionService.submitSimple(
                memberId,
                sessionId,
                before.revision()
        );

        assertEquals("SUBMITTED", submitted.status());

        // 제출 이후에도 실제 세션 응답으로 사용자 RESULT를 생성한다.
        var result = candidateService.buildSimpleResultViewFromSession(
                memberId,
                sessionId.toString(),
                q08EvidenceService
        );

        assertNotNull(result);
        assertEquals(
                "DIRECT_MULTI_PREFERENCE_RESULT",
                result.status()
        );
        assertEquals(
                java.util.List.of("R01", "R02"),
                result.preferredStyleIds()
        );
        assertNull(result.representativeStyleId());
        assertFalse(result.comprehensiveResult());
    }

    // 제출 이후 RESULT 컨트롤러가 제출 상태와 선호 스타일을 반환하는지 확인한다.
// 동일 테스트 트랜잭션에서 실행하므로 제출 변경 사항은 종료 시 롤백된다.
    @Test
    void returnsSubmittedSimpleResultResponse() {
        String memberId = "dev-member-002";

        java.util.UUID sessionId = java.util.UUID.fromString(
                "b656c1a6-8013-49de-8e1a-29203e9800c1"
        );

        var before = sessionService.get(memberId, sessionId);
        assertEquals("IN_PROGRESS", before.status());

        // 실제 저장된 10문항 답변을 제출한다.
        var submitted = sessionService.submitSimple(
                memberId,
                sessionId,
                before.revision()
        );
        assertEquals("SUBMITTED", submitted.status());

        // 컨트롤러의 인증 회원과 세션 소유자를 일치시킨다.
        var authentication = new UsernamePasswordAuthenticationToken(
                memberId,
                null
        );

        var response = simpleResultController.getSimpleResult(
                sessionId,
                authentication
        );

        // 제출 완료와 스타일 종합 판별 미확정을 구분한다.
        assertTrue(response.submitted());
        assertTrue(response.preview());

        assertEquals(
                "DIRECT_MULTI_PREFERENCE_RESULT",
                response.result().status()
        );
        assertEquals(
                java.util.List.of("R01", "R02"),
                response.result().preferredStyleIds()
        );
        assertFalse(response.result().comprehensiveResult());

        // API에 표시할 스타일 이름도 DB 정의에서 조회한다.
        assertEquals(
                java.util.List.of("R01", "R02"),
                response.preferredStyles()
                        .stream()
                        .map(SimpleResultController.PreferredStyle::styleId)
                        .toList()
        );
    }

}
