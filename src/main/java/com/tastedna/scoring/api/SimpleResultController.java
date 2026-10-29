package com.tastedna.scoring.api;

import com.tastedna.scoring.application.Q08ComparisonEvidenceService;
import com.tastedna.scoring.application.SessionObservationService;
import com.tastedna.scoring.application.StyleCoreCandidateService;
import com.tastedna.scoring.domain.ResultCandidate;
import com.tastedna.scoring.persistence.ResultCandidateRepository;
import com.tastedna.session.application.TestSessionService;

import com.tastedna.session.persistence.SessionAnswerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

// Simple 테스트의 사용자용 RESULT 조회 API.
// 현재 판별 설정이 개발용 JSON에 의존하므로 local 환경에서만 제공한다.
@RestController
@RequestMapping("/api/v1/sessions")
@Profile("local")
public class SimpleResultController {

    private final TestSessionService sessionService;
    private final StyleCoreCandidateService candidateService;
    private final Q08ComparisonEvidenceService q08EvidenceService;
    private final SessionObservationService observationService;
    private final ResultCandidateRepository candidateRepository;
    private final SessionAnswerRepository answerRepository;

    public SimpleResultController(
            TestSessionService sessionService,
            StyleCoreCandidateService candidateService,
            Q08ComparisonEvidenceService q08EvidenceService,
            SessionObservationService observationService,
            ResultCandidateRepository candidateRepository,
            SessionAnswerRepository answerRepository
    ) {
        this.sessionService = sessionService;
        this.candidateService = candidateService;
        this.q08EvidenceService = q08EvidenceService;
        this.observationService = observationService;
        this.candidateRepository = candidateRepository;
        this.answerRepository = answerRepository;
    }

    // 사용자에게 표시할 스타일의 기본 정보.
    public record PreferredStyle(
            String styleId,
            String styleName
    ) {
    }

    // 쇼핑몰이 선택지 ID의 출처와 응답 버전을 구분할 수 있도록 한다.
    // definitionId는 질문 정의, revision은 현재 세션 답변 버전이다.
    public record SimpleResultResponse(
            UUID definitionId,
            long revision,
            boolean preview,
            boolean submitted,
            StyleCoreCandidateService.SimpleResultView result,
            List<PreferredStyle> preferredStyles,
            List<String> recommendationStyleIds,
            Map<String, List<String>> directPreferences,
            Map<String, List<String>> directPreferenceOptionIds
    ) {
    }

    // GET /api/v1/sessions/{sessionId}/simple-result
    // 인증된 회원이 소유한 Simple 세션의 RESULT를 반환한다.
    @GetMapping("/{sessionId}/simple-result")
    public SimpleResultResponse getSimpleResult(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        String memberId = authentication.getName();

        // 기존 세션 서비스로 회원 소유권을 확인한다.
        var session = sessionService.get(
                memberId,
                sessionId
        );

        if (!"SIMPLE".equals(session.mode().name())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SIMPLE_SESSION_REQUIRED"
            );
        }

        // 현재 Simple 테스트는 Q01~Q10 응답이 모두 있어야
        // 사용자용 RESULT 미리보기를 생성할 수 있다.
        var answeredQuestionIds = answerRepository
                .findByIdSessionIdOrderByIdQuestionIdAsc(
                        sessionId.toString()
                )
                .stream()
                .map(answer -> answer.getId().getQuestionId())
                .collect(java.util.stream.Collectors.toSet());

        Set<String> requiredQuestionIds = Set.of(
                "Q01", "Q02", "Q03", "Q04", "Q05",
                "Q06", "Q07", "Q08", "Q09", "Q10"
        );

        if (!answeredQuestionIds.containsAll(requiredQuestionIds)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SIMPLE_ANSWERS_INCOMPLETE"
            );
        }

        // 검증된 스타일 판별 규칙 없이도 표시할 수 있는 직접 선호만 추출한다.
// Q01·Q03·Q04에서 실제로 선택한 값만 사용자 RESULT에 포함한다.
        var rawObservations = observationService.collectRaw(
                memberId,
                sessionId.toString()
        );

        Map<String, List<String>> directPreferences =
                new java.util.LinkedHashMap<>();

        Map<String, List<String>> directPreferenceOptionIds =
                new java.util.LinkedHashMap<>();

        Map<String, String> preferenceTypes = Map.of(
                "colors", "DIRECT_COLOR_PREFERENCE",
                "silhouettes", "DIRECT_SILHOUETTE_PREFERENCE",
                "patterns", "DIRECT_PATTERN_GRAPHIC_PREFERENCE"
        );

        for (String key : List.of("colors", "silhouettes", "patterns")) {
            String observationType = preferenceTypes.get(key);

            List<String> values = rawObservations
                    .getOrDefault(observationType, List.of())
                    .stream()
                    .filter(observation ->
                            "EXPLICIT_SELECTION".equals(observation.evidence()))
                    .map(observation -> observation.observedValue())
                    .distinct()
                    .toList();

            directPreferences.put(key, values);

            // 쇼핑몰이 표시 문구에 의존하지 않도록 원래 선택지 ID도 제공한다.
            List<String> optionIds = rawObservations
                    .getOrDefault(observationType, List.of())
                    .stream()
                    .filter(observation ->
                            "EXPLICIT_SELECTION".equals(observation.evidence()))
                    .map(observation -> observation.optionId())
                    .distinct()
                    .toList();

            directPreferenceOptionIds.put(key, optionIds);
        }

        // 저장된 응답으로 사용자용 RESULT를 생성한다.
        var result = candidateService.buildSimpleResultViewFromSession(
                memberId,
                sessionId.toString(),
                q08EvidenceService
        );

        // 실제 RESULT에 포함된 후보만 DB에서 일괄 조회한다.
        Set<String> preferredIds =
                Set.copyOf(result.preferredStyleIds());

        List<PreferredStyle> preferredStyles =
                candidateRepository.findByCandidateIdIn(preferredIds)
                        .stream()
                        .filter(ResultCandidate::isStyleCore)
                        .sorted(Comparator.comparing(
                                ResultCandidate::getCandidateId
                        ))
                        .map(candidate -> new PreferredStyle(
                                candidate.getCandidateId(),
                                candidate.getCandidateName()
                        ))
                        .toList();

        // 판별된 후보 ID가 DB의 Style Core 정의와 모두 대응하는지 검증한다.
        // 일부 후보가 누락된 응답을 정상 RESULT로 반환하지 않는다.
        Set<String> resolvedIds = preferredStyles.stream()
                .map(PreferredStyle::styleId)
                .collect(java.util.stream.Collectors.toSet());

        if (!resolvedIds.equals(preferredIds)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "RESULT_CANDIDATE_DEFINITION_INCOMPLETE"
            );
        }

        return new SimpleResultResponse(
                session.definitionId(),
                session.revision(),
                true,
                "SUBMITTED".equals(session.status()),
                result,
                preferredStyles,
                List.of(),
                directPreferences,
                directPreferenceOptionIds
        );
    }
}
