package com.tastedna.session;

import com.tastedna.definition.domain.*;
import com.tastedna.definition.persistence.*;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.api.dto.*;
import com.tastedna.session.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WomensSessionStartTest {
    final String id = WomensImageDefinitionTest.ID;
    final TestDefinitionRepository definitions=mock(TestDefinitionRepository.class);
    final QuestionDefinitionRepository questions=mock(QuestionDefinitionRepository.class);
    final TestSessionRepository sessions=mock(TestSessionRepository.class);
    final SessionAnswerRepository answers=mock(SessionAnswerRepository.class);
    final TestSessionService service=new TestSessionService(definitions,questions,sessions,answers,WomensImageDefinitionTest.MAPPER);
    List<QuestionDefinition> prepare() throws Exception {
        var definition=mock(TestDefinition.class);
        when(definition.getDefinitionId()).thenReturn(id);
        when(definition.getTestMode()).thenReturn("SIMPLE");
        when(definition.getVersionCode()).thenReturn("SIMPLE-WOMENS-IMAGE-V1");
        when(definition.getQuestionCount()).thenReturn((short)10);
        when(definitions.findById(id)).thenReturn(Optional.of(definition));
        var payloads=new TreeMap<>(ImageDefinitionTest.questions());
        payloads.putAll(WomensImageDefinitionTest.questions());payloads.putAll(WomensFinalDefinitionTest.questions());
        var rows=new ArrayList<QuestionDefinition>();
        for(var e:payloads.entrySet()) {
            var q=mock(QuestionDefinition.class);
            when(q.getId()).thenReturn(new QuestionDefinitionId(id,e.getKey()));
            when(q.getQuestionPayload()).thenReturn(e.getValue().toString());
            when(q.isRequired()).thenReturn(true);rows.add(q);
        }
        when(questions.findByIdDefinitionIdOrderByDisplayOrderAsc(id)).thenReturn(rows);
        return rows;
    }
    @Test void completeDedicatedDefinitionStartsThroughBothEntryPoints() throws Exception {
        prepare();
        for(boolean generic:List.of(false,true)) {
            var result=generic?service.start("member",new StartSessionRequest(Mode.SIMPLE,ApparelScope.womens))
                    :service.startImageSimple("member",ApparelScope.womens);
            assertEquals(UUID.fromString(id),result.definitionId());assertEquals(ApparelScope.womens,result.apparelScope());
        }
        verify(definitions,never()).findByTestModeAndActiveTrue(anyString());
        verify(sessions,times(2)).saveAndFlush(argThat(s->s.getDefinitionId().equals(id)&&s.getApparelScope().equals("WOMENS")));
    }
    @Test void incompleteOrInvalidDefinitionsNeverPersistSession() throws Exception {
        var rows=prepare();
        when(questions.findByIdDefinitionIdOrderByDisplayOrderAsc(id)).thenReturn(rows.subList(0,7));
        assertEquals(422,assertThrows(ResponseStatusException.class,()->service.startImageSimple("member",ApparelScope.womens)).getStatusCode().value());
        when(questions.findByIdDefinitionIdOrderByDisplayOrderAsc(id)).thenReturn(rows);
        var q=(ObjectNode)WomensImageDefinitionTest.MAPPER.readTree(rows.get(7).getQuestionPayload());
        ((ObjectNode)q.path("comparisons").get(3)).remove("minSelections");
        when(rows.get(7).getQuestionPayload()).thenReturn(q.toString());
        assertEquals(422,assertThrows(ResponseStatusException.class,()->service.startImageSimple("member",ApparelScope.womens)).getStatusCode().value());
        verify(sessions,never()).saveAndFlush(any());
    }
    @Test void existingSessionIsProtected() throws Exception {
        prepare();
        when(sessions.findByMallMemberIdAndStatusIn("member",List.of("IN_PROGRESS")))
                .thenReturn(List.of(mock(com.tastedna.session.domain.TestSession.class)));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.startImageSimple("member",ApparelScope.womens)).getStatusCode().value());
        verify(sessions,never()).saveAndFlush(any());
    }
}
