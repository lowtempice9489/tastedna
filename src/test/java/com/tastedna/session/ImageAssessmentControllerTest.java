package com.tastedna.session;

import com.tastedna.session.api.ImageAssessmentController;
import com.tastedna.session.api.dto.ApparelScope;
import com.tastedna.session.application.TestSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DB 없이 기존 경로의 기본 범위와 인증 주체 전달, 미준비 응답을 검증한다.
class ImageAssessmentControllerTest {
    @Test void defaultsToMensAndRejectsUnreadyWomens() throws Exception {
        var service = mock(TestSessionService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ImageAssessmentController(service)).build();
        var member = new UsernamePasswordAuthenticationToken("member-owner", null);
        when(service.startImageSimple("member-owner", ApparelScope.womens))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "IMAGE_DEFINITION_NOT_READY"));

        mvc.perform(post("/api/v1/sessions/simple-image").principal(member)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/sessions/simple-image").param("apparelScope", "mens").principal(member))
                .andExpect(status().isOk());
        verify(service, times(2)).startImageSimple("member-owner", ApparelScope.mens);
        mvc.perform(post("/api/v1/sessions/simple-image").param("apparelScope", "womens").principal(member))
                .andExpect(status().is(422));
        verify(service).startImageSimple("member-owner", ApparelScope.womens);
        mvc.perform(post("/api/v1/sessions/simple-image").param("apparelScope", "unknown").principal(member))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }
}
