package com.tastedna.member;

import com.tastedna.config.SecurityConfig;
import com.tastedna.session.api.TestSessionController;
import com.tastedna.session.application.TestSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(controllers = {TestSessionController.class, com.tastedna.config.FormClientConfigController.class}, properties = "DEV_MEMBER_PASSWORD=local-test-only-password")
@ActiveProfiles("local")
@Import(SecurityConfig.class)
class LocalAuthenticationRegressionTest {
    @Autowired MockMvc mvc;
    @MockitoBean TestSessionService service;
    @Test void localFrontendModeIsExplicitAndPublic() throws Exception {
        mvc.perform(get("/api/v1/form/config")).andExpect(status().isOk()).andExpect(jsonPath("$.authMode").value("local"));
        mvc.perform(get("/form-auth.mjs")).andExpect(status().isOk());
    }
    @Test void existingThreeDevelopmentIdentitiesAreUnchanged() throws Exception {
        for (String name : new String[]{"dev-member-001", "dev-member-002", "dev-image-demo"}) {
            mvc.perform(get("/api/v1/sessions/active").with(httpBasic(name, "local-test-only-password")))
                    .andExpect(status().isNoContent());
            verify(service).findActive(name);
        }
    }
}
