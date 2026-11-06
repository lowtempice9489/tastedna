package com.tastedna.session;

import com.tastedna.session.application.comparison.*;
import com.tastedna.session.application.multi.MultiChoiceAnswerValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WomensFinalDefinitionTest {
    static Map<String, JsonNode> questions() throws Exception {
        Map<String, JsonNode> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of("src/main/resources/db/migration/V19__add_womens_simple_q08_q10.sql")))
            if (line.startsWith("INSERT INTO td_question_definition"))
                rows.put("Q%02d".formatted(rows.size()+8), WomensImageDefinitionTest.MAPPER.readTree(
                        line.substring(line.indexOf(",'{")+2, line.lastIndexOf("','{}',TRUE)"))));
        return rows;
    }
    static ObjectNode answer(JsonNode q, boolean all) {
        var root = WomensImageDefinitionTest.MAPPER.createObjectNode();
        var selections = root.putArray("selections");
        for (var group : q.path("comparisons")) {
            var row = selections.addObject().put("comparisonId", group.path("id").asString());
            if (ComparisonAnswerValidator.isRanked(group)) {
                var ids = row.putArray("selectedOptionIds");
                if (all) for (int i=group.path("options").size()-1;i>=0;i--) ids.add(group.path("options").get(i).path("id").asString());
                else ids.add("B");
            } else row.put("selectedOptionId", "A");
        }
        return root;
    }
    @Test void fixtureIdsImagesAndAllTenQuestionsArePreserved() throws Exception {
        var rows = questions();
        assertEquals(Set.of("Q08","Q09","Q10"), rows.keySet());
        for (var entry : rows.entrySet()) {
            var fixture = WomensImageDefinitionTest.MAPPER.readTree(Files.readString(Path.of("dev/fixtures/simple/womens_simple_"+entry.getKey().toLowerCase()+"_draft.json"))).path("questionPayload");
            if (entry.getKey().equals("Q10")) compareOptions(fixture, entry.getValue());
            else {
                assertEquals(fixture.path("comparisons").size(),entry.getValue().path("comparisons").size());
                for(int i=0;i<fixture.path("comparisons").size();i++) {
                    var expected=fixture.path("comparisons").get(i);var actual=entry.getValue().path("comparisons").get(i);
                    assertEquals(expected.path("id"),actual.path("id"));
                    assertEquals(expected.path("selectionMode"),actual.path("selectionMode"));
                    assertEquals(expected.path("overviewImages"),actual.path("overviewImages"));
                    for(var image:actual.path("overviewImages")) exists(image);
                    assertFalse(actual.has("showWhen"));compareOptions(expected,actual);
                    if(ComparisonAnswerValidator.isRanked(actual)) {
                        assertEquals(1,actual.path("minSelections").asInt());
                        assertEquals(actual.path("options").size(),actual.path("maxSelections").asInt());
                    }
                }
            }
        }
        Set<String> ids=new TreeSet<>(rows.keySet());ids.addAll(WomensImageDefinitionTest.questions().keySet());
        ids.addAll(List.of("Q01","Q02","Q03","Q04"));
        assertEquals(java.util.stream.IntStream.rangeClosed(1,10).mapToObj(i->"Q%02d".formatted(i)).toList(),new ArrayList<>(ids));
    }
    void compareOptions(JsonNode expected,JsonNode actual) {
        assertEquals(expected.path("options").size(),actual.path("options").size());
        for(int i=0;i<expected.path("options").size();i++) {
            assertEquals(expected.path("options").get(i).path("id"),actual.path("options").get(i).path("id"));
            assertEquals(expected.path("options").get(i).path("images"),actual.path("options").get(i).path("images"));
            for(var image:actual.path("options").get(i).path("images")) exists(image);
        }
    }
    void exists(JsonNode image){assertTrue(Files.isRegularFile(Path.of(image.asString().substring("/assets/".length()))));}
    @Test void singleAndRankedContractsValidateAndKeepOrderWithoutGeneralNegativeInference() throws Exception {
        for(String qid:List.of("Q08","Q09")) {
            var q=questions().get(qid);
            for(boolean all:List.of(false,true)) {
                var response=answer(q,all);
                ComparisonAnswerValidator.validate(q.toString(),response,WomensImageDefinitionTest.MAPPER);
                var selections=ComparisonAnswerPayloadExtractor.extractSelections(response);
                for(var group:q.path("comparisons")) {
                    var s=selections.get(group.path("id").asString());
                    if(!s.ranked()||all) assertTrue(s.notPreferredIds(group).isEmpty());
                    else assertEquals(group.path("options").size()-1,s.notPreferredIds(group).size());
                    if(s.ranked()&&all) assertEquals(group.path("options").get(group.path("options").size()-1).path("id").asString(),s.optionIds().getFirst());
                }
            }
            var base=answer(q,false);
            for(int i=0;i<q.path("comparisons").size();i++) {
                boolean ranked=ComparisonAnswerValidator.isRanked(q.path("comparisons").get(i));
                for(String invalid:ranked?List.of("[]","[\"A\",\"A\"]","[\"UNKNOWN\"]"):List.of("[\"A\"]")) {
                    var bad=base.deepCopy();var row=(ObjectNode)bad.path("selections").get(i);
                    row.remove("selectedOptionId");row.set("selectedOptionIds",WomensImageDefinitionTest.MAPPER.readTree(invalid));
                    assertThrows(IllegalArgumentException.class,()->ComparisonAnswerValidator.validate(q.toString(),bad,WomensImageDefinitionTest.MAPPER));
                }
            }
            var missing=base.deepCopy();((ArrayNode)missing.path("selections")).remove(0);
            assertThrows(IllegalArgumentException.class,()->ComparisonAnswerValidator.validate(q.toString(),missing,WomensImageDefinitionTest.MAPPER));
        }
    }
    @Test void q10IsOrdinaryMultipleChoiceUpToSix() throws Exception {
        var q=questions().get("Q10");assertEquals(19,q.path("options").size());
        var mapper=WomensImageDefinitionTest.MAPPER;
        for(int size:List.of(1,6)) assertDoesNotThrow(()->MultiChoiceAnswerValidator.validate(q.toString(),mapper.valueToTree(Map.of("selectedOptionIds",List.of("A","B","C","D","E","F").subList(0,size))),mapper));
        for(var ids:List.of(List.of("A","B","C","D","E","F","G"),List.of("NONE"),List.of("A","A")))
            assertThrows(IllegalArgumentException.class,()->MultiChoiceAnswerValidator.validate(q.toString(),mapper.valueToTree(Map.of("selectedOptionIds",ids)),mapper));
        assertFalse(q.has("selectionMode"));
    }
}
