package com.tastedna.session;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class WomensSharedDefinitionTest {
    @Test void migrationCopiesOnlyApprovedSharedContractWithExistingDraftIdentity() throws Exception {
        var mapper = JsonMapper.builder().build();
        var questions = ImageDefinitionTest.questions();
        String draft = Files.readString(Path.of("dev/fixtures/simple/womens_simple_q05_q06_q10_definition_draft.sql"));
        for (int n = 1; n <= 4; n++) {
            String qid = "Q%02d".formatted(n);
            var matcher = Pattern.compile("SET @womens_" + qid.toLowerCase() + "_payload = '(.*?)';").matcher(draft);
            assertTrue(matcher.find());
            assertEquals(questions.get(qid), mapper.readTree(matcher.group(1)));
        }
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V17__add_inactive_womens_simple_shared_questions.sql"));
        assertTrue(draft.contains("6acc799d-1348-4f34-b756-593e9f5bfd52"));
        assertTrue(sql.contains("'SIMPLE-WOMENS-IMAGE-V1'"));
        assertTrue(sql.contains("UTC_TIMESTAMP(6), FALSE"));
        assertEquals(2, sql.split("question_id IN", -1).length - 1);
        assertEquals(2, sql.split(Pattern.quote("('Q01', 'Q02', 'Q03', 'Q04')"), -1).length - 1);
        assertEquals(3, sql.split("INSERT INTO", -1).length - 1);
        assertFalse(Pattern.compile("(?im)^\\s*(UPDATE|DELETE|DROP|REPLACE|TRUNCATE)\\b").matcher(sql).find());
    }
}
