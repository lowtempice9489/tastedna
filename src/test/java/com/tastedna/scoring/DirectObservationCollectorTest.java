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

class DirectObservationCollectorTest {

    @Test
    void collectsSelectedOptionObservations() {

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
