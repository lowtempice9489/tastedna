package com.tastedna.scoring.application;

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

@Service
@RequiredArgsConstructor
public class SessionObservationService {

    private final SessionAnswerRepository answerRepository;
    private final TestSessionRepository sessionRepository;
    private final DirectObservationCollector collector;
    private final JsonMapper mapper;

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

        return RawObservationAggregator.aggregate(
                collected,
                answerRevisions
        );
    }
}
