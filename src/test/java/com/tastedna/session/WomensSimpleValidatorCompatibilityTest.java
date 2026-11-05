package com.tastedna.session;

import com.tastedna.session.application.comparison.ComparisonAnswerPayloadExtractor;
import com.tastedna.session.application.comparison.ComparisonAnswerValidator;
import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

// 초안의 확정된 구조만 검증한다. 최소 선택 수·NONE·조건을 보충하거나 제품 정책으로 승인하지 않는다.
class WomensSimpleValidatorCompatibilityTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode payload(String question) throws Exception {
        return mapper.readTree(Files.readString(Path.of("dev/fixtures/simple/womens_simple_"
                + question.toLowerCase() + "_draft.json"))).path("questionPayload");
    }

    private JsonNode answer(Object value) {
        return mapper.readTree(mapper.writeValueAsString(value));
    }

    @ParameterizedTest
    @CsvSource({"Q05,3", "Q06,3", "Q10,6"})
    void multipleChoiceMaximumAndOptionIdsAreSupported(String question, int maximum) throws Exception {
        String definition = payload(question).toString();
        List<String> selected = IntStream.range(0, maximum).mapToObj(i -> String.valueOf((char) ('A' + i))).toList();
        assertDoesNotThrow(() -> MultiChoiceAnswerValidator.validate(definition,
                answer(Map.of("selectedOptionIds", selected)), mapper));
        var tooMany = new java.util.ArrayList<>(selected);
        tooMany.add(String.valueOf((char) ('A' + maximum)));
        assertEquals("TOO_MANY_OPTIONS_SELECTED", assertThrows(IllegalArgumentException.class,
                () -> MultiChoiceAnswerValidator.validate(definition,
                        answer(Map.of("selectedOptionIds", tooMany)), mapper)).getMessage());
        for (var invalid : List.of(List.of("UNKNOWN"), List.of("A", "A"))) {
            assertThrows(IllegalArgumentException.class, () -> MultiChoiceAnswerValidator.validate(definition,
                    answer(Map.of("selectedOptionIds", invalid)), mapper));
        }
        // 빈 배열 허용 여부는 여성복의 미확정 계약이므로 여기서 승인된 정책으로 검증하지 않는다.
    }

    private JsonNode group(String question, String id) throws Exception {
        for (JsonNode g : payload(question).path("comparisons")) {
            if (id.equals(g.path("id").asString())) return g;
        }
        throw new AssertionError(id);
    }

    @ParameterizedTest
    @CsvSource({"Q08,C01", "Q08,C02", "Q08,C03", "Q09,C01", "Q09,C03", "Q09,C04"})
    void singleComparisonGroupsAcceptTheirOptionsAndRejectArrays(String question, String id) throws Exception {
        JsonNode g = group(question, id);
        // 순위형의 미확정 최소값 때문에 전체 문항은 검증할 수 없어 기존 그룹만 격리한다.
        String definition = mapper.writeValueAsString(Map.of("kind", "COMPARISON", "comparisons", List.of(g)));
        for (JsonNode option : g.path("options")) {
            assertDoesNotThrow(() -> ComparisonAnswerValidator.validate(definition,
                    answer(Map.of("selections", List.of(Map.of("comparisonId", id,
                            "selectedOptionId", option.path("id").asString())))), mapper));
        }
        for (var invalid : List.of(Map.of("comparisonId", id, "selectedOptionId", "UNKNOWN"),
                Map.of("comparisonId", id, "selectedOptionIds", List.of("A")))) {
            assertThrows(IllegalArgumentException.class, () -> ComparisonAnswerValidator.validate(definition,
                    answer(Map.of("selections", List.of(invalid))), mapper));
        }
    }

    @ParameterizedTest
    @CsvSource({"Q08,C04", "Q08,C05", "Q09,C02"})
    void rankedOrderExtractionWorksButMissingMinimumBlocksDefinition(String question, String id) throws Exception {
        JsonNode g = group(question, id);
        assertTrue(ComparisonAnswerValidator.isRanked(g));
        assertFalse(g.has("minSelections"));
        JsonNode submitted = answer(Map.of("selections", List.of(Map.of("comparisonId", id,
                "selectedOptionIds", List.of("B", "A")))));
        var extracted = ComparisonAnswerPayloadExtractor.extractSelections(submitted).get(id);
        assertTrue(extracted.ranked());assertEquals(List.of("B", "A"), extracted.optionIds());
        assertThrows(IllegalArgumentException.class, () -> ComparisonAnswerPayloadExtractor.extractSelections(
                answer(Map.of("selections", List.of(Map.of("comparisonId", id,
                        "selectedOptionIds", List.of("A", "A")))))));
        String definition = payload(question).toString();
        assertEquals("INVALID_COMPARISON_DEFINITION", assertThrows(IllegalArgumentException.class,
                () -> ComparisonAnswerValidator.validate(definition, submitted, mapper)).getMessage());
        // 정의 오류가 먼저 발생하므로 여성복 순위형의 허용 ID 판정·완전한 답변 검증은 최소값 승인 후 확인한다.
    }
}
