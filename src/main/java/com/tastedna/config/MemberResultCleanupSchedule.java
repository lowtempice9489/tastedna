package com.tastedna.config;

import com.tastedna.session.application.MemberResultCleanupService;
import com.tastedna.session.persistence.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Configuration
@Profile("member-auth")
@EnableScheduling
@ConditionalOnProperty(name = "tastedna.result-cleanup.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MemberResultCleanupSchedule {
    private static final Logger log = LoggerFactory.getLogger(MemberResultCleanupSchedule.class);
    private final TestSessionRepository sessions;
    private final MemberResultCleanupService cleanup;

    // 검증 전 자동 실행을 막기 위해 명시적으로 활성화한다. 기본 실행 시각은 매일 UTC 03:00이다.
    @Scheduled(cron = "${tastedna.result-cleanup.cron:0 0 3 * * *}", zone = "UTC")
    public void cleanDaily() {
        var cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(5);
        String afterId = "";
        int deleted = 0;
        int failed = 0;
        while (true) {
            var batch = sessions.findCleanupCandidates(afterId, cutoff, PageRequest.of(0, 50));
            if (batch.isEmpty()) break;
            for (var candidate : batch) {
                afterId = candidate.getSessionId();
                try {
                    if (cleanup.deleteExpired(candidate.getMallMemberId(), candidate.getSessionId())) deleted++;
                } catch (RuntimeException failure) {
                    // 해당 세션은 롤백하고 다음 후보로 진행한다. 실패 대상은 다음 일일 실행에서 재검토한다.
                    failed++;
                    log.warn("TasteDNA cleanup transaction failed: {}", failure.getClass().getSimpleName());
                }
            }
            if (batch.size() < 50) break;
        }
        log.info("TasteDNA cleanup finished: deleted={}, failed={}", deleted, failed);
    }
}
