package com.tastedna.member;

import com.tastedna.config.MemberSecurityConfig;
import com.tastedna.member.api.MemberAuthController;
import com.tastedna.member.application.*;
import com.tastedna.member.domain.FormMember;
import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.definition.persistence.*;
import com.tastedna.scoring.api.PreferenceSummaryController;
import com.tastedna.scoring.application.PreferenceSummaryService;
import com.tastedna.session.api.*;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.application.MemberResultService;
import com.tastedna.session.application.MemberResultCleanupService;
import com.tastedna.session.application.MemberResultExpiryService;
import com.tastedna.session.application.GuestSimpleService;
import com.tastedna.session.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

// 실제 필터·세션·CSRF·컨트롤러·서비스를 검증한다. Repository만 대역이므로 MySQL 통합 테스트는 아니다.
@WebMvcTest(controllers = {MemberAuthController.class, TestSessionController.class, MemberResultController.class, GuestSimpleController.class,
        ImageAssessmentController.class, PreferenceSummaryController.class, com.tastedna.scoring.api.SimpleInitialCandidateController.class,
        com.tastedna.demo.DemoProductMatchController.class,
        com.tastedna.config.FormClientConfigController.class, com.tastedna.demo.DemoProductController.class})
@Import({MemberResultCleanupService.class, MemberResultService.class, GuestSimpleService.class, MemberSecurityConfig.class, MemberAuthService.class, TestSessionService.class, PreferenceSummaryService.class,
        com.tastedna.scoring.application.SimpleInitialCandidateService.class, com.tastedna.demo.DemoProductMatchService.class})
@ActiveProfiles("member-auth")
class MemberAuthHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired PasswordEncoder encoder;
    @MockitoBean FormMemberRepository members;
    @MockitoBean TestDefinitionRepository definitions;
    @MockitoBean QuestionDefinitionRepository questions;
    @MockitoBean TestSessionRepository sessions;
    @MockitoBean SessionAnswerRepository answers;
    @MockitoBean MemberResultRetentionRepository retention;
    @MockitoBean MemberResultExpiryService expiryService;
    @MockitoBean AuthAttemptLimiter limiter;
    @MockitoBean com.tastedna.demo.DemoProductRepository products;
    @MockitoBean com.tastedna.scoring.application.StyleCoreCandidateService initialCandidates;
    @MockitoBean com.tastedna.scoring.application.SessionObservationService candidateObservations;
    @MockitoBean com.tastedna.scoring.persistence.ResultCandidateRepository candidateCatalog;
    private final Map<String, FormMember> stored = new HashMap<>();
    private static final String PASSWORD = "a long test password";
    private record Browser(MockHttpSession session, String token) {}

    @Test void guestEndpointsDoNotOpenMemberHistoryOrManagementAndStillRequireCsrf() throws Exception {
        mvc.perform(get("/api/v1/member/results")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sessions/history")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/member/results/" + UUID.randomUUID() + "/save").with(csrf()))
                .andExpect(status().isUnauthorized());
        // 인증 없이 정의 조회 서비스까지 도달하되, 준비되지 않은 정의는 기존 422 계약을 따른다.
        mvc.perform(get("/api/v1/guest/simple/questions").param("apparelScope", "mens"))
                .andExpect(status().is(422));
        mvc.perform(post("/api/v1/guest/simple/submit").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/guest/simple/submit").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(retention, answers, sessions);
    }

    @BeforeEach void prepare() {
        stored.clear();
        when(members.existsByEmail(anyString())).thenAnswer(call -> stored.containsKey(call.getArgument(0)));
        when(members.findByEmail(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(members.findById(anyString())).thenAnswer(call -> stored.values().stream()
                .filter(m -> m.getMemberId().equals(call.getArgument(0))).findFirst());
        when(members.saveAndFlush(any())).thenAnswer(call -> {
            FormMember member = call.getArgument(0);
            stored.put(member.getEmail(), member); return member;
        });

        when(expiryService.review(anyString(), any(UUID.class)))
                .thenAnswer(invocation -> new MemberResultExpiryService.Review(
                        invocation.getArgument(1).toString(),
                        MemberResultExpiryService.Status.TEMPORARY,
                        null
                ));
    }
    private Browser csrfToken(MockHttpSession session) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN")).andReturn();
        return new Browser((MockHttpSession) result.getRequest().getSession(),
                mapper.readTree(result.getResponse().getContentAsString()).path("token").asString());
    }
    private String credentials(String email, String password) {
        return mapper.writeValueAsString(Map.of("email", email, "password", password));
    }
    private MockHttpServletRequestBuilder protectedPost(String path, Browser browser) {
        return post(path).session(browser.session()).header("X-CSRF-TOKEN", browser.token()).contentType("application/json");
    }
    private Browser signupAndLogin(String email) throws Exception {
        var browser = csrfToken(null);
        mvc.perform(protectedPost("/api/v1/auth/signup", browser).content(credentials(email, PASSWORD))).andExpect(status().isCreated());
        mvc.perform(protectedPost("/api/v1/auth/login", browser).content(credentials(email, PASSWORD))).andExpect(status().isOk());
        return csrfToken(browser.session());
    }

    @Test void anonymousFormAssetsAndReadOnlyProductsArePublicButMemberApisStayProtected() throws Exception {
        mvc.perform(get("/api/v1/form/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.authMode").value("member"));
        when(products.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());
        mvc.perform(get("/api/v1/demo/products")).andExpect(status().isOk()).andExpect(content().json("[]"));
        for (String path : List.of("/index.html", "/app.js", "/app.css", "/shop.mjs", "/shop.css",
                "/assessment-state.mjs", "/form-auth.mjs"))
            mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/sessions/active")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/demo/products").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test void signupHashLoginFixationCsrfRotationMeAndLogout() throws Exception {
        var browser = csrfToken(null);
        String oldId = browser.session().getId();
        mvc.perform(protectedPost("/api/v1/auth/signup", browser).content(credentials("  Alice@Example.COM  ", PASSWORD)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertNull(browser.session().getAttribute("SPRING_SECURITY_CONTEXT"));
        var member = stored.get("alice@example.com");
        assertTrue(member.getMemberId().startsWith("form-"));
        assertNotEquals(PASSWORD, member.getPasswordHash());
        assertTrue(encoder.matches(PASSWORD, member.getPasswordHash()));
        mvc.perform(get("/api/v1/auth/me").session(browser.session())).andExpect(status().isUnauthorized());
        mvc.perform(protectedPost("/api/v1/auth/login", browser).content(credentials("ALICE@example.com", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberId").value(member.getMemberId()));
        assertNotEquals(oldId, browser.session().getId());
        var context = (SecurityContext) browser.session().getAttribute("SPRING_SECURITY_CONTEXT");
        assertEquals(member.getMemberId(), context.getAuthentication().getName());
        assertNull(context.getAuthentication().getCredentials());
        mvc.perform(get("/api/v1/auth/me").session(browser.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
        // 로그인 전 CSRF는 더 이상 보호 요청에 사용할 수 없다.
        mvc.perform(protectedPost("/api/v1/auth/logout", browser)).andExpect(status().isForbidden());
        var loggedIn = csrfToken(browser.session());
        mvc.perform(protectedPost("/api/v1/auth/logout", loggedIn)).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("FORMSESSION", 0));
        assertTrue(loggedIn.session().isInvalid());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void normalizedDuplicateAndConcurrentUniqueViolationReturn409() throws Exception {
        var browser = csrfToken(null);
        mvc.perform(protectedPost("/api/v1/auth/signup", browser).content(credentials("case@example.com", PASSWORD))).andExpect(status().isCreated());
        mvc.perform(protectedPost("/api/v1/auth/signup", browser).content(credentials(" CASE@EXAMPLE.COM ", PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        doThrow(new DataIntegrityViolationException("simulated unique constraint")).when(members).saveAndFlush(any());
        mvc.perform(protectedPost("/api/v1/auth/signup", browser).content(credentials("race@example.com", PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test void invalidInputsAreRejectedWithoutSaving() throws Exception {
        var browser = csrfToken(null);
        for (var pair : List.of(new String[]{"not-email", PASSWORD}, new String[]{"a@example.com", "short"},
                new String[]{"a@example.com", "x".repeat(73)}, new String[]{"a@example.com", "가".repeat(25)},
                new String[]{"a@example.com", "password\nwithcontrol"})) {
            mvc.perform(protectedPost("/api/v1/auth/signup", browser).content(credentials(pair[0], pair[1])))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verify(members, never()).saveAndFlush(any());
    }

    @Test void wrongPasswordAndMissingEmailHaveSameErrorAndNoAuthentication() throws Exception {
        var browser = csrfToken(null);
        mvc.perform(protectedPost("/api/v1/auth/signup", browser).content(credentials("known@example.com", PASSWORD))).andExpect(status().isCreated());
        for (String email : List.of("known@example.com", "missing@example.com")) {
            mvc.perform(protectedPost("/api/v1/auth/login", browser).content(credentials(email, "wrong long password")))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
            assertNull(browser.session().getAttribute("SPRING_SECURITY_CONTEXT"));
        }
    }

    @Test void unauthenticatedBasicAndCsrfLessRequestsAreRejected() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String path : List.of("/api/v1/auth/me", "/api/v1/sessions/active", "/api/v1/sessions/" + id,
                "/api/v1/sessions/" + id + "/answers", "/api/v1/sessions/" + id + "/preference-summary",
                "/api/v1/sessions/" + id + "/initial-candidates", "/api/v1/sessions/" + id + "/matched-demo-products"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/v1/auth/me").with(httpBasic("dev-member-001", "irrelevant"))).andExpect(status().isUnauthorized());
        for (String path : List.of("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/logout"))
            mvc.perform(post(path).contentType("application/json").content(credentials("a@example.com", PASSWORD)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var browser = signupAndLogin("csrf@example.com");
        mvc.perform(post("/api/v1/sessions").session(browser.session()).contentType("application/json")
                .content("{\"mode\":\"SIMPLE\",\"apparelScope\":\"mens\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(get("/api/v1/sessions/" + id + "/raw-observations").session(browser.session()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/auth/me").with(user(stored.get("csrf@example.com").getMemberId()).roles("OTHER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test void disabledExistingSessionsAreRevokedAcrossAssessmentEndpoints() throws Exception {
        String email = "revoked@example.com";
        var first = signupAndLogin(email);
        var other = signupAndLogin("unaffected@example.com");
        String id = UUID.randomUUID().toString();
        var paths = List.of("/api/v1/auth/me", "/api/v1/sessions/active", "/api/v1/sessions/" + id,
                "/api/v1/sessions/" + id + "/answers", "/api/v1/sessions/" + id + "/preference-summary",
                "/api/v1/sessions", "/api/v1/sessions/simple-image",
                "/api/v1/sessions/" + id + "/answers", "/api/v1/sessions/" + id + "/submit");
        var browsers = new ArrayList<Browser>();
        browsers.add(first);
        for (int i = 1; i < paths.size(); i++) {
            var browser = csrfToken(null);
            mvc.perform(protectedPost("/api/v1/auth/login", browser).content(credentials(email, PASSWORD)))
                    .andExpect(status().isOk());
            browsers.add(csrfToken(browser.session()));
        }
        var original = stored.get(email);
        var disabled = spy(original);
        when(disabled.getStatus()).thenReturn("DISABLED");
        stored.put(email, disabled);
        for (int i = 0; i < paths.size(); i++) {
            var browser = browsers.get(i);
            var context = (SecurityContext) browser.session().getAttribute("SPRING_SECURITY_CONTEXT");
            var request = i < 5 ? get(paths.get(i)).session(browser.session())
                    : protectedPost(paths.get(i), browser).content("{}");
            mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                    .andExpect(cookie().maxAge("FORMSESSION", 0));
            assertTrue(browser.session().isInvalid());
            assertNull(context.getAuthentication());
        }
        verifyNoInteractions(sessions, answers);
        mvc.perform(get("/api/v1/auth/me").session(other.session())).andExpect(status().isOk());
        assertFalse(other.session().isInvalid());
        stored.put(email, original);
        // 상태를 복구해도 폐기된 세션의 인증 정보는 되살아나지 않는다.
        assertTrue(first.session().isInvalid());
        mvc.perform(get("/api/v1/auth/me").cookie(new jakarta.servlet.http.Cookie("FORMSESSION", first.session().getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test void missingMemberIsRevokedEvenWithoutCsrf() throws Exception {
        var browser = signupAndLogin("removed@example.com");
        stored.remove("removed@example.com");
        mvc.perform(post("/api/v1/sessions").session(browser.session()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(cookie().maxAge("FORMSESSION", 0));
        assertTrue(browser.session().isInvalid());
        verifyNoInteractions(sessions, answers);
    }

    @Test void disabledAccountHasGenericLoginFailureAndLimiterReturns429() throws Exception {
        var disabled = mock(FormMember.class);
        when(disabled.getMemberId()).thenReturn("form-disabled");
        when(disabled.getPasswordHash()).thenReturn(encoder.encode(PASSWORD));
        when(disabled.getRole()).thenReturn("MEMBER");
        when(disabled.getStatus()).thenReturn("DISABLED");
        stored.put("disabled@example.com", disabled);
        var browser = csrfToken(null);
        mvc.perform(protectedPost("/api/v1/auth/login", browser).content(credentials("disabled@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "AUTH_RATE_LIMITED")).when(limiter).check(anyString());
        mvc.perform(protectedPost("/api/v1/auth/login", browser).content(credentials("unknown@example.com", PASSWORD)))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
        verify(members, never()).findByEmail("unknown@example.com");
    }

    @Test void otherMembersSessionAnswersResultsAndWritesAreNotAccessible() throws Exception {
        var browser = signupAndLogin("a@example.com");
        var owner = new FormMember("form-" + UUID.randomUUID(), "b@example.com", "unused", java.time.LocalDateTime.now());
        stored.put(owner.getEmail(), owner);
        String memberA = stored.get("a@example.com").getMemberId();
        String sessionB = UUID.randomUUID().toString();
        var now = java.time.LocalDateTime.now();
        var ownedByB = new com.tastedna.session.domain.TestSession(sessionB, owner.getMemberId(),
                UUID.randomUUID().toString(), "MENS", "[\"Q01\"]", now, now.plusMinutes(2));
        when(sessions.findBySessionIdAndMallMemberId(eq(sessionB), anyString())).thenAnswer(call ->
                owner.getMemberId().equals(call.getArgument(1)) ? Optional.of(ownedByB) : Optional.empty());
        when(sessions.findByIdAndMemberIdForUpdate(eq(sessionB), anyString())).thenAnswer(call ->
                owner.getMemberId().equals(call.getArgument(1)) ? Optional.of(ownedByB) : Optional.empty());
        for (String suffix : List.of("", "/answers", "/questions", "/preference-summary", "/initial-candidates", "/matched-demo-products"))
            mvc.perform(get("/api/v1/sessions/" + sessionB + suffix).session(browser.session()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        mvc.perform(protectedPost("/api/v1/sessions/" + sessionB + "/answers", browser)
                .content("{\"questionId\":\"Q01\",\"expectedRevision\":0,\"answerPayload\":{\"selectedOptionIds\":[\"A\"]}}"))
                .andExpect(status().isNotFound());
        mvc.perform(protectedPost("/api/v1/sessions/" + sessionB + "/submit", browser).content("{\"expectedRevision\":0}"))
                .andExpect(status().isNotFound());
        verify(sessions, atLeastOnce()).findByIdAndMemberIdForUpdate(sessionB, memberA);
        verify(sessions, atLeastOnce()).findBySessionIdAndMallMemberId(sessionB, memberA);
        verifyNoInteractions(answers);
    }

    @Test void registeredMemberCompletesExistingMensAssessmentWithInternalOwnership() throws Exception {
        var browser = signupAndLogin("flow@example.com");
        String memberId = stored.get("flow@example.com").getMemberId();
        String definitionId = "33333333-3333-4333-8333-333333333333";
        var definition = mock(com.tastedna.definition.domain.TestDefinition.class);
        when(definition.getDefinitionId()).thenReturn(definitionId);
        when(definition.getQuestionCount()).thenReturn((short) 10);
        when(definition.getTestMode()).thenReturn("SIMPLE");
        when(definitions.findById(definitionId)).thenReturn(Optional.of(definition));
        var questionRows = new ArrayList<com.tastedna.definition.domain.QuestionDefinition>();
        for (var entry : com.tastedna.session.ImageDefinitionTest.questions().entrySet()) {
            var question = mock(com.tastedna.definition.domain.QuestionDefinition.class);
            var key = new com.tastedna.definition.domain.QuestionDefinitionId(definitionId, entry.getKey());
            when(question.getId()).thenReturn(key);
            when(question.getQuestionPayload()).thenReturn(entry.getValue().toString());
            when(questions.findById(key)).thenReturn(Optional.of(question));
            questionRows.add(question);
        }
        when(questions.findByIdDefinitionIdOrderByDisplayOrderAsc(definitionId)).thenReturn(questionRows);
        var start = mvc.perform(protectedPost("/api/v1/sessions/simple-image", browser))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(start.getResponse().getContentAsString()).path("sessionId").asString();
        var sessionCaptor = org.mockito.ArgumentCaptor.forClass(com.tastedna.session.domain.TestSession.class);
        verify(sessions).saveAndFlush(sessionCaptor.capture());
        var session = sessionCaptor.getValue();
        assertEquals(memberId, session.getMallMemberId());
        assertNotEquals("flow@example.com", session.getMallMemberId());
        when(sessions.findBySessionIdAndMallMemberId(id, memberId)).thenReturn(Optional.of(session));
        when(sessions.findByIdAndMemberIdForUpdate(id, memberId)).thenReturn(Optional.of(session));
        when(sessions.findByMallMemberIdAndStatusIn(eq(memberId), anyList())).thenReturn(List.of(session));
        mvc.perform(get("/api/v1/sessions/active").session(browser.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id));
        var saved = new LinkedHashMap<String, com.tastedna.session.domain.SessionAnswer>();
        when(answers.save(any())).thenAnswer(call -> {
            com.tastedna.session.domain.SessionAnswer answer = call.getArgument(0);
            saved.put(answer.getId().getQuestionId(), answer); return answer;
        });
        when(answers.findByIdSessionIdOrderByIdQuestionIdAsc(id)).thenAnswer(call -> new ArrayList<>(saved.values()));
        String[] payloads = {"{\"selectedOptionIds\":[\"A\"]}", "{\"selectedOptionIds\":[\"R01\"]}",
                "{\"selectedOptionIds\":[\"S02\"]}", "{\"selectedOptionIds\":[\"P01\"]}",
                "{\"selectedOptionId\":\"A\"}", "{\"selectedOptionId\":\"G\"}", "{\"selectedOptionIds\":[\"NONE\"]}",
                "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"A\"},{\"comparisonId\":\"C02\",\"selectedOptionId\":\"A\"},{\"comparisonId\":\"C03\",\"selectedOptionId\":\"B\"}]}",
                "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"B\"},{\"comparisonId\":\"C02\",\"selectedOptionId\":\"E\"},{\"comparisonId\":\"C03\",\"selectedOptionId\":\"A\"}]}",
                "{\"selectedOptionId\":\"J\"}"};
        for (int i = 0; i < 10; i++) {
            mvc.perform(protectedPost("/api/v1/sessions/" + id + "/answers", browser)
                    .content(mapper.writeValueAsString(Map.of("questionId", "Q%02d".formatted(i+1),
                            "expectedRevision", i, "answerPayload", mapper.readTree(payloads[i])))))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/sessions/" + id + "/answers").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answers.Q10.selectedOptionId").value("J"));
        mvc.perform(protectedPost("/api/v1/sessions/" + id + "/submit", browser).content("{\"expectedRevision\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        verifyNoInteractions(retention);
        assertEquals(session.getSubmittedAt().plusDays(5), session.getTemporaryResultExpiresAt());
        mvc.perform(get("/api/v1/sessions/" + id + "/preference-summary").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DIRECT_PREFERENCE_SUMMARY"))
                .andExpect(jsonPath("$.comprehensiveResult").value(false));
        when(answers.findById(new com.tastedna.session.domain.SessionAnswerId(id, "Q02"))).thenReturn(Optional.of(saved.get("Q02")));
        when(questions.findByIdDefinitionIdAndIdQuestionId(definitionId, "Q02")).thenReturn(Optional.of(questionRows.get(1)));
        mvc.perform(get("/api/v1/sessions/" + id + "/initial-candidates").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INITIAL_CANDIDATES"))
                .andExpect(jsonPath("$.finalResult").value(false)).andExpect(jsonPath("$.candidates").isEmpty())
                .andExpect(jsonPath("$.decision.status").value("DEFERRED"))
                .andExpect(jsonPath("$.decision.results").isEmpty())
                .andExpect(jsonPath("$.decision.missingRequirements[0]").value("MAPPED_DIRECT_PREFERENCE_EVIDENCE"));
        verify(candidateObservations).collectRaw(memberId, id);
        verify(initialCandidates).extractCompatibleDirectEvidence(eq(definitionId), any(), any(), any());
        when(answers.findById(new com.tastedna.session.domain.SessionAnswerId(id, "Q06"))).thenReturn(Optional.of(saved.get("Q06")));
        mvc.perform(get("/api/v1/sessions/" + id + "/matched-demo-products").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.matchType").value("EXPLICIT_STYLE_ATTRIBUTE"))
                .andExpect(jsonPath("$.products").isEmpty());
    }
}
