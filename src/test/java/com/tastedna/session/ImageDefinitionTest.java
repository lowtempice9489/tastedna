package com.tastedna.session;

import com.tastedna.session.application.comparison.ComparisonAnswerValidator;
import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import com.tastedna.session.application.single.SingleChoiceAnswerValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class ImageDefinitionTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    // 별도 JSON fixture 없이 실제 배포 마이그레이션의 질문을 검증한다.
    public static Map<String, JsonNode> questions() throws Exception {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of("src/main/resources/db/migration/V13__add_mens_image_simple_definition.sql"))) {
            if (!line.startsWith("INSERT INTO td_question_definition")) continue;
            int start = line.indexOf(",'{") + 2;
            int end = line.lastIndexOf("','{}',TRUE)");
            String id = "Q%02d".formatted(result.size() + 1);
            result.put(id, MAPPER.readTree(line.substring(start, end).replace("''", "'").replace("\\\\", "\\")));
        }
        return result;
    }
    private JsonNode answer(String... selections) {
        var rows = new ArrayList<Map<String, String>>();
        for (int i = 0; i < selections.length; i += 2)
            rows.add(Map.of("comparisonId", selections[i], "selectedOptionId", selections[i+1]));
        return MAPPER.valueToTree(Map.of("selections", rows));
    }
    @Test void allTenQuestionsAndEveryReferencedImageExist() throws Exception {
        var questions = questions();
        assertEquals(10, questions.size());
        for (var entry : questions.entrySet()) {
            JsonNode q = entry.getValue();
            if (entry.getKey().compareTo("Q04") <= 0) assertFalse(q.toString().contains("/assets/"));
            checkImages(q);
        }
        assertEquals(7, questions.get("Q06").path("options").size());
        assertEquals(8, questions.get("Q07").path("options").size());
        assertEquals(10, questions.get("Q10").path("options").size());
    }
    private void checkImages(JsonNode node) {
        if (node.isString() && node.stringValue().startsWith("/assets/gender/"))
            assertTrue(Files.isRegularFile(Path.of(node.stringValue().substring("/assets/".length()))), node.stringValue());
        if (node.isContainer()) for (JsonNode child : node) checkImages(child);
    }
    @Test void pocketFollowupRequiredOnlyWhenPocketSelected() throws Exception {
        String question = questions().get("Q08").toString();
        assertDoesNotThrow(() -> ComparisonAnswerValidator.validate(question, answer("C01","A","C02","A","C03","C"), MAPPER));
        assertDoesNotThrow(() -> ComparisonAnswerValidator.validate(question, answer("C01","B","C02","C","C02_POCKETS","B","C03","B"), MAPPER));
        assertThrows(IllegalArgumentException.class, () -> ComparisonAnswerValidator.validate(question, answer("C01","A","C02","B","C03","A"), MAPPER));
        assertThrows(IllegalArgumentException.class, () -> ComparisonAnswerValidator.validate(question, answer("C01","A","C02","A","C02_POCKETS","A","C03","A"), MAPPER));
    }
    @Test void acceptsFiveWayStylingAndRejectsUnknownOrMissingGroups() throws Exception {
        String question = questions().get("Q09").toString();
        assertDoesNotThrow(() -> ComparisonAnswerValidator.validate(question, answer("C01","A","C02","E","C03","B"), MAPPER));
        assertThrows(IllegalArgumentException.class, () -> ComparisonAnswerValidator.validate(question, answer("C01","A","C02","F","C03","B"), MAPPER));
        assertThrows(IllegalArgumentException.class, () -> ComparisonAnswerValidator.validate(question, answer("C01","A","C02","A"), MAPPER));
    }
    @Test void existingThreePairDefinitionStillWorks() throws Exception {
        String question = Files.readString(Path.of("dev/fixtures/simple/simple_q08_question_payload.json"));
        assertDoesNotThrow(() -> ComparisonAnswerValidator.validate(question, answer("C01","A","C02","B","C03","A"), MAPPER));
    }
    @Test void newGarmentAndFinalOptionsAndExclusiveAvoidanceWork() throws Exception {
        var q = questions();
        SingleChoiceAnswerValidator.validate(q.get("Q06").toString(), MAPPER.readTree("{\"selectedOptionId\":\"G\"}"), MAPPER);
        SingleChoiceAnswerValidator.validate(q.get("Q10").toString(), MAPPER.readTree("{\"selectedOptionId\":\"J\"}"), MAPPER);
        MultiChoiceAnswerValidator.validate(q.get("Q07").toString(), MAPPER.readTree("{\"selectedOptionIds\":[\"NONE\"]}"), MAPPER);
        assertThrows(IllegalArgumentException.class, () -> MultiChoiceAnswerValidator.validate(q.get("Q07").toString(), MAPPER.readTree("{\"selectedOptionIds\":[\"NONE\",\"G\"]}"), MAPPER));
    }
    @Test void malformedConditionalDefinitionIsRejected() {
        String question = """
                {"kind":"COMPARISON","comparisons":[
                 {"id":"C01","showWhen":{"comparisonId":"C02","optionIds":["A"]},"options":[{"id":"A"},{"id":"B"}]}]}
                """;
        assertThrows(IllegalArgumentException.class, () -> ComparisonAnswerValidator.validate(question, answer("C01","A"), MAPPER));
    }
}

