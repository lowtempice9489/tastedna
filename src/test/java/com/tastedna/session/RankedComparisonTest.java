package com.tastedna.session;

import com.tastedna.session.application.comparison.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

// 활성 정의와 개발 DB를 바꾸지 않는 공통 계약 검증이다. fixture의 최소 수는 제품 결정이 아니다.
public class RankedComparisonTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    public static String definition() throws Exception {
        return Files.readString(Path.of("dev/fixtures/simple/ranked_comparison_question_payload.json"));
    }
    public static String answer(String ids) {
        return "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"A\"},"
                + "{\"comparisonId\":\"C02\",\"selectedOptionIds\":" + ids + "}]}";
    }
    private void validate(String definition, String answer) {
        ComparisonAnswerValidator.validate(definition, mapper.readTree(answer), mapper);
    }
    @Test void preservesOrderAndDerivesNonPreferenceOnlyForRankedGroup() throws Exception {
        String answer = answer("[\"B\",\"A\"]");
        validate(definition(), answer);
        var extracted = ComparisonAnswerPayloadExtractor.extractSelections(mapper.readTree(answer));
        assertEquals(List.of("B", "A"), extracted.get("C02").optionIds());
        JsonNode groups = mapper.readTree(definition()).path("comparisons");
        assertEquals(List.of("C"), extracted.get("C02").notPreferredIds(groups.get(1)));
        assertTrue(extracted.get("C01").notPreferredIds(groups.get(0)).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> extracted.get("C02").optionIds().add("C"));
    }
    @Test void rejectsDuplicatesUnknownIdsWrongFieldsAndClientEvidence() throws Exception {
        String definition = definition();
        for (String ids : List.of("[\"A\",\"A\"]", "[\"Z\"]", "[null]", "[1]", "[\"\"]", "null", "\"A\""))
            assertThrows(IllegalArgumentException.class, () -> validate(definition, answer(ids)), ids);
        String valid = answer("[\"B\",\"A\"]");
        for (String invalid : List.of(
                valid.replace("\"selectedOptionId\":\"A\"", "\"selectedOptionIds\":[\"A\"]"),
                valid.replace("\"selectedOptionIds\":[\"B\",\"A\"]", "\"selectedOptionId\":\"B\""),
                valid.replace("\"selectedOptionIds\":", "\"selectedOptionId\":\"B\",\"selectedOptionIds\":"),
                valid.replace("\"comparisonId\":\"C02\"", "\"comparisonId\":\"C01\""),
                valid.replace("\"comparisonId\":\"C02\"", "\"comparisonId\":\"UNKNOWN\""),
                valid.replace("\"selectedOptionIds\":", "\"score\":10,\"selectedOptionIds\":"),
                valid.replace("\"selectedOptionIds\":", "\"notPreferred\":[\"C\"],\"selectedOptionIds\":")))
            assertThrows(IllegalArgumentException.class, () -> validate(definition, invalid), invalid);
    }
    @Test void minimumIsExplicitAndZeroIsAllowedOnlyWhenConfigured() throws Exception {
        String definition = definition();
        assertThrows(IllegalArgumentException.class, () -> validate(definition, answer("[]")));
        validate(definition.replace("\"minSelections\": 1", "\"minSelections\": 0"), answer("[]"));
        validate(definition.replace("\"minSelections\": 1", "\"minSelections\": 3"), answer("[\"C\",\"A\",\"B\"]"));
        assertThrows(IllegalArgumentException.class, () -> validate(definition.replace("\"minSelections\": 1", "\"minSelections\": 3"), answer("[\"B\",\"A\"]")));
        for (String minimum : List.of("null", "-1", "4", "1.5", "\"1\"", "2147483648"))
            assertThrows(IllegalArgumentException.class, () -> validate(definition.replace("\"minSelections\": 1", "\"minSelections\": " + minimum), answer("[\"B\"]")));
        assertThrows(IllegalArgumentException.class, () -> validate(definition.replace("\"minSelections\": 1,", ""), answer("[\"B\"]")));
        assertThrows(IllegalArgumentException.class, () -> validate(definition.replace("RANKED_MULTI_SELECT", "RANKED_TYPO"), answer("[\"B\"]")));
    }
    @Test void conditionalRankedChildRetainsRequiredAndHiddenAnswerValidation() throws Exception {
        String definition = definition().replace("\"selectionMode\":", "\"showWhen\":{\"comparisonId\":\"C01\",\"optionIds\":[\"A\"]},\"selectionMode\":");
        validate(definition, answer("[\"B\",\"A\"]"));
        assertThrows(IllegalArgumentException.class, () -> validate(definition, answer("[\"B\"]").replace("\"selectedOptionId\":\"A\"", "\"selectedOptionId\":\"B\"")));
        validate(definition, "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"B\"}]}");
        assertThrows(IllegalArgumentException.class, () -> validate(definition, "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"A\"}]}"));
    }
}
