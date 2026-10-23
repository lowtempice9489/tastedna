package com.tastedna.scoring;

import com.tastedna.scoring.application.DirectObservationCollector;
import com.tastedna.scoring.application.SelectedOptionRuleResolver;
import com.tastedna.scoring.domain.OptionScoringRule;
import com.tastedna.scoring.domain.OptionScoringRuleId;
import com.tastedna.scoring.persistence.OptionScoringRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 DB를 사용하지 않고 가짜 관측 규칙을 만들어 직접 관측 수집 기능을 검증한다.
// 선택한 옵션이 올바른 관측 데이터로 연결되는지 확인하는 단위 테스트다.
class DirectObservationCollectorTest {

    // Q01에서 A를 선택했을 때 연결된 색상 선호 관측이 생성되는지 확인한다.
    // 관측 결과의 질문 ID, 선택지 ID, 관측 내용이 예상값과 일치해야 한다.
    @Test
    void collectsSelectedOptionObservations() {

        // Mockito로 실제 DB를 대신할 Repository를 생성한다.
        // DB 연결 상태와 관계없이 관측 수집 로직 자체를 검사하기 위한 설정이다.
        OptionScoringRuleRepository repository =
                mock(OptionScoringRuleRepository.class);

        SelectedOptionRuleResolver resolver =
                new SelectedOptionRuleResolver(repository);

        DirectObservationCollector collector =
                new DirectObservationCollector(resolver);

        String definitionId =
                "22222222-2222-4222-8222-222222222222";

        OptionScoringRule rule = mock(OptionScoringRule.class);

        when(rule.getId()).thenReturn(
                new OptionScoringRuleId(definitionId, "Q01", "A")
        );

        when(rule.getObservationPayload()).thenReturn("""
        {"type":"DIRECT_COLOR_PREFERENCE",
         "observedValue":"블랙",
         "evidence":"EXPLICIT_SELECTION"}
        """);

        ReflectionTestUtils.setField(
                rule,
                "id",
                new OptionScoringRuleId(
                        definitionId,
                        "Q01",
                        "A"
                )
        );

        ReflectionTestUtils.setField(
                rule,
                "observationPayload",
                """
                {"type":"DIRECT_COLOR_PREFERENCE",
                 "observedValue":"블랙",
                 "evidence":"EXPLICIT_SELECTION"}
                """
        );

        when(repository.findByIdDefinitionIdAndIdQuestionId(
                definitionId,
                "Q01"
        )).thenReturn(List.of(rule));

        var observations = collector.collect(
                definitionId,
                "Q01",
                List.of("A")
        );

        // 선택한 옵션 하나에서 관측 하나가 생성됐는지 확인한다.
        // 질문·선택지 식별자와 원본 관측 내용이 유지되는지도 함께 검사한다.
        assertEquals(1, observations.size());
        assertEquals("Q01", observations.getFirst().questionId());
        assertEquals("A", observations.getFirst().optionId());
        assertTrue(
                observations.getFirst()
                        .observationPayload()
                        .contains("블랙")
        );
    }
}
