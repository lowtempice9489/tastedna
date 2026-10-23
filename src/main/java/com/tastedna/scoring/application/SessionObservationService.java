package com.tastedna.scoring.application;

import com.tastedna.session.application.comparison.ComparisonAnswerPayloadExtractor;
import com.tastedna.session.application.multi.MultiChoiceAnswerPayloadExtractor;
import com.tastedna.session.application.single.SingleChoiceAnswerPayloadExtractor;
import com.tastedna.session.domain.SessionAnswer;
import com.tastedna.session.domain.TestSession;
import com.tastedna.session.persistence.SessionAnswerRepository;
import com.tastedna.session.persistence.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 세션에 저장된 답변을 질문 유형에 따라 해석하고 Raw 관측으로 변환한다.
// 회원의 세션 접근 권한과 답변 데이터의 정합성을 확인하며, 최종 RESULT 판별은 수행하지 않는다.
@Service
@RequiredArgsConstructor
public class SessionObservationService {

    private final SessionAnswerRepository answerRepository;
    private final TestSessionRepository sessionRepository;
    private final DirectObservationCollector collector;
    private final JsonMapper mapper;

    // 지정된 회원의 세션에서 저장된 답변을 읽어 관측 유형별로 묶어 반환한다.
    // 데이터베이스를 변경하지 않는 읽기 전용 처리이며, 답변이 없으면 관측을 생성하지 않는다.
    @Transactional(readOnly = true)
    public Map<String, List<RawObservationAggregator.RawObservation>>
    collectRaw(String memberId, String sessionId) {

        TestSession session = sessionRepository
                .findBySessionIdAndMallMemberId(
                        sessionId,
                        memberId
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "SESSION_NOT_FOUND"
                ));

        String definitionId = session.getDefinitionId();

        List<SessionAnswer> answers = new ArrayList<>(
                answerRepository
                        .findByIdSessionIdOrderByIdQuestionIdAsc(sessionId)
        );

        if (answers.isEmpty()) {
            throw new IllegalStateException(
                    "RAW_OBSERVATION_NOT_READY"
            );
        }

        // 세션 시작 시 확정된 질문 순서를 기준으로 답변을 정렬한다.
        // 순서가 중복되거나 세션에 없는 질문의 답변이 발견되면 처리를 중단한다.
        String[] questionOrder;

        try {
            questionOrder = mapper.readValue(
                    session.getQuestionOrder(),
                    String[].class
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "INVALID_SESSION_QUESTION_ORDER",
                    e
            );
        }

        Map<String, Integer> questionPositions =
                new HashMap<>();

        for (int i = 0; i < questionOrder.length; i++) {
            if (questionPositions.putIfAbsent(
                    questionOrder[i], i
            ) != null) {
                throw new IllegalStateException(
                        "INVALID_SESSION_QUESTION_ORDER"
                );
            }
        }

        // 저장된 질문 내용과 답변을 읽고 SINGLE, MULTIPLE, COMPARISON 유형별로 선택지를 해석한다.
        // 답변 Revision도 함께 보관해 관측이 어떤 답변 버전에서 생성됐는지 추적할 수 있게 한다.
        for (SessionAnswer answer : answers) {
            if (!questionPositions.containsKey(
                    answer.getId().getQuestionId()
            )) {
                throw new IllegalStateException(
                        "ANSWER_NOT_IN_SESSION_QUESTION_ORDER"
                );
            }
        }

        answers.sort(Comparator.comparingInt(
                answer -> questionPositions.get(
                        answer.getId().getQuestionId()
                )
        ));

        List<DirectObservationCollector.ParsedObservation> collected =
                new ArrayList<>();

        Map<String, Long> answerRevisions =
                new HashMap<>();

        for (SessionAnswer answer : answers) {

            answerRevisions.put(
                    answer.getId().getQuestionId(),
                    answer.getAnswerRevision()
            );

            JsonNode question;
            JsonNode payload;

            try {
                question = mapper.readTree(
                        answer.getPresentedPayload()
                );
                payload = mapper.readTree(
                        answer.getAnswerPayload()
                );
            } catch (Exception e) {
                throw new IllegalStateException(
                        "INVALID_STORED_ANSWER",
                        e
                );
            }

            JsonNode kindNode = question.get("kind");

            String kind = kindNode != null && kindNode.isString()
                    ? kindNode.stringValue()
                    : null;

            List<String> selectedOptionIds;

            if ("MULTIPLE_CHOICE".equals(kind)) {
                selectedOptionIds =
                        MultiChoiceAnswerPayloadExtractor.extract(
                                payload
                        );

            } else if ("SINGLE_CHOICE".equals(kind)) {
                selectedOptionIds = List.of(
                        SingleChoiceAnswerPayloadExtractor.extract(
                                payload
                        )
                );

            } else if ("COMPARISON".equals(kind)) {
                var selections =
                        ComparisonAnswerPayloadExtractor.extract(
                                payload
                        );

                // 비교형 답변은 C01:A처럼 비교쌍 ID와 선택지 ID를 함께 보존한다.
                // 단순 A/B 선택으로 변환하면 서로 다른 비교쌍의 의미가 섞일 수 있다.
                JsonNode comparisons = question.get("comparisons");

                if (comparisons == null
                        || !comparisons.isArray()
                        || comparisons.size() != selections.size()) {
                    throw new IllegalStateException(
                            "INVALID_STORED_COMPARISON"
                    );
                }

                List<String> comparisonOptions =
                        new ArrayList<>();

                for (JsonNode comparison : comparisons) {
                    JsonNode comparisonIdNode =
                            comparison.get("id");

                    if (comparisonIdNode == null
                            || !comparisonIdNode.isString()) {
                        throw new IllegalStateException(
                                "INVALID_STORED_COMPARISON"
                        );
                    }

                    String comparisonId =
                            comparisonIdNode.stringValue();

                    String selectedOptionId =
                            selections.get(comparisonId);

                    if (selectedOptionId == null) {
                        throw new IllegalStateException(
                                "INVALID_STORED_COMPARISON"
                        );
                    }

                    comparisonOptions.add(
                            comparisonId + ":" + selectedOptionId
                    );
                }

                selectedOptionIds = List.copyOf(
                        comparisonOptions
                );

            } else {
                throw new IllegalStateException(
                        "OBSERVATION_KIND_NOT_SUPPORTED"
                );
            }

            collected.addAll(
                    collector.collectParsed(
                            definitionId,
                            answer.getId().getQuestionId(),
                            selectedOptionIds,
                            mapper
                    )
            );
        }

        // 수집한 직접 관측을 관측 유형별로 묶고 원본 답변의 Revision을 연결한다.
        // 여기서는 RESULT 점수나 최종 스타일을 계산하지 않는다.
        return RawObservationAggregator.aggregate(
                collected,
                answerRevisions
        );
    }
}
