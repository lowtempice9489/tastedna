package com.tastedna.member;

import com.tastedna.config.*;
import com.tastedna.session.api.TestSessionController;
import com.tastedna.session.application.TestSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(controllers = TestSessionController.class)
@Import(SecurityConfig.class)
class DefaultSecurityRegressionTest {
    @Autowired MockMvc mvc;
    @MockitoBean TestSessionService service;
    @Test void defaultEnvironmentRemainsClosedEvenWithBasicCredentials() throws Exception {
        mvc.perform(get("/api/v1/sessions/active").with(httpBasic("dev-image-demo", "irrelevant")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void memberAndLocalProfilesCannotBeCombined() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local", "member-auth");
        assertThrows(IllegalStateException.class, () -> new MemberSecurityConfig(environment));
    }
}
