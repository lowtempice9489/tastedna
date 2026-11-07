package com.tastedna.member;
import java.nio.file.Path;
import java.nio.file.Files;
import org.springframework.transaction.annotation.Transactional;
import com.tastedna.demo.DemoProduct;
import com.tastedna.demo.DemoProductRepository;

import com.tastedna.member.domain.FormMember;
import com.tastedna.member.persistence.FormMemberRepository;
import com.tastedna.session.persistence.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 명시적으로 실행한 경우에만 고정된 테스트 DB를 사용한다. 실제 Repository와 트랜잭션을 대체하지 않는다.
// 새 검증 행만 추가하고 기존 데이터나 테스트 정의를 삭제·수정하지 않는다.
@EnabledIfEnvironmentVariable(named = "RUN_MEMBER_MYSQL_TESTS", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"member-auth", "test"})
@Import(MemberAuthMysqlIntegrationTest.SafeMigration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberAuthMysqlIntegrationTest {
    @Autowired DemoProductRepository demoProducts;

    // 기존 테스트 DB 사전 확인을 재사용하며 카탈로그를 읽기만 한다.
    @Test
    @Transactional
    void demoCatalogV25HasExpectedStyleCoverage() {
        assertEquals("25", flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        var catalog = demoProducts.findAllByOrderByDisplayOrderAsc();
        assertEquals(100, catalog.size());
        assertEquals(100, catalog.stream().map(DemoProduct::getProductId).distinct().count());
        assertEquals(100, catalog.stream().map(DemoProduct::getImageUrl).distinct().count());
        assertEquals(26, catalog.stream().filter(p -> p.getStyleCodes().isEmpty()).count());
        assertEquals(29, catalog.stream().filter(p -> p.getApparelScope() == DemoProduct.ApparelScope.MENS).count());
        assertEquals(71, catalog.stream().filter(p -> p.getApparelScope() == DemoProduct.ApparelScope.WOMENS).count());
        assertEquals(70, catalog.stream().filter(p -> !p.getColorCodes().isEmpty()).count());
        assertEquals(30, catalog.stream().filter(p -> p.getColorCodes().isEmpty()).count());
        var allowed = new HashSet<String>();
        for (int i = 1; i <= 12; i++) allowed.add("R%02d".formatted(i));
        for (var product : catalog) {
            assertTrue(product.getColorCodes().stream().allMatch(c -> c.matches("[A-O]")));
            assertTrue(allowed.containsAll(product.getStyleCodes()));
            assertTrue(product.getImageUrl().startsWith("/assets/gender/"));
            assertTrue(Files.isRegularFile(Path.of(product.getImageUrl().substring(8))));
        }
        int[] expected = {10, 0, 12, 6, 16, 8, 7, 0, 11, 2, 1, 1};
        for (int i = 0; i < expected.length; i++) {
            String code = "R%02d".formatted(i + 1);
            var matched = demoProducts.findMatchingStyles(Set.of(code));
            assertEquals(expected[i], matched.size(), code);
            assertTrue(matched.stream().allMatch(p -> p.getStyleCodes().contains(code)));
        }
    }
    private static final String TEST_URL = "jdbc:mysql://localhost:3306/tastedna_test?connectionTimeZone=UTC";
    private static final Map<String, Map<String, List<String>>> baseline = new LinkedHashMap<>();
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired FormMemberRepository members;
    @Autowired TestSessionRepository sessions;
    @Autowired SessionAnswerRepository answers;
    @Autowired PasswordEncoder encoder;
    @Autowired Flyway flyway;
    private final String password = UUID.randomUUID().toString();
    private record Browser(MockHttpSession session, String token) {}

    @DynamicPropertySource
    static void testDatabaseOnly(DynamicPropertyRegistry properties) {
        if (!"coql".equals(System.getenv("DB_USERNAME")) || System.getenv("DB_PASSWORD") == null)
            throw new IllegalStateException("Dedicated test credentials are required");
        properties.add("spring.datasource.url", () -> TEST_URL);
        properties.add("spring.datasource.username", () -> "coql");
        properties.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        properties.add("spring.flyway.url", () -> TEST_URL);
        properties.add("spring.flyway.user", () -> "coql");
        properties.add("spring.flyway.password", () -> System.getenv("DB_PASSWORD"));
        properties.add("spring.flyway.clean-disabled", () -> true);
        properties.add("spring.flyway.locations", () -> "classpath:db/migration");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.show-sql", () -> false);
        properties.add("logging.level.org.hibernate.orm.jdbc.bind", () -> "OFF");
        properties.add("logging.level.org.hibernate.SQL", () -> "OFF");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SafeMigration {
        @Bean FlywayMigrationStrategy verifiedTestDatabaseMigration() {
            return migration -> {
                // JPA 초기화보다 먼저 대상 DB를 검사해야 하므로 이 테스트 안전 검사에만 JDBC를 사용한다.
                // 회원 업무 로직은 기존 Spring Data JPA를 그대로 사용한다.
                try (Connection connection = migration.getConfiguration().getDataSource().getConnection()) {
                    assertEquals("tastedna_test", connection.getCatalog());
                    assertTrue(connection.getMetaData().getUserName().startsWith("coql@"));
                    baseline.clear();
                    baseline.put("td_test_session", snapshot(connection, "td_test_session"));
                    baseline.put("td_session_answer", snapshot(connection, "td_session_answer"));
                } catch (java.sql.SQLException exception) {
                    throw new IllegalStateException("Test database preflight failed", exception);
                }
                migration.migrate();
            };
        }
    }

    // 마이그레이션 전후 기존 세션·답변의 실제 저장값을 비교한다. 값과 해시는 로그로 출력하지 않는다.
    private static Map<String, List<String>> snapshot(Connection connection, String table) throws java.sql.SQLException {
        try (var tables = connection.getMetaData().getTables("tastedna_test", null, table, new String[]{"TABLE"})) {
            if (!tables.next()) return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        String sql = "td_test_session".equals(table) ? "SELECT * FROM td_test_session" : "SELECT * FROM td_session_answer";
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                String key = rows.getString("session_id") + ("td_session_answer".equals(table) ? ":" + rows.getString("question_id") : "");
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                    String column = rows.getMetaData().getColumnName(i);
                    // V12가 승인된 필터 기준으로 재정의하는 생성 컬럼은 원본 응답 데이터가 아니다.
                    if (!"active_member_id".equals(column)) values.add(column + "=" + rows.getString(i));
                }
                result.put(key, values);
            }
        }
        return result;
    }

    @AfterAll void existingSessionAndAnswerRowsRemainUnchanged() throws Exception {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
            for (var table : baseline.entrySet()) {
                var after = snapshot(connection, table.getKey());
                for (var before : table.getValue().entrySet())
                    assertTrue(Objects.equals(before.getValue(), after.get(before.getKey())), "Existing test data changed");
            }
        }
    }

    @Test void flywayAndJpaSchemaValidationReachV25() {
        assertEquals("25", flyway.info().current().getVersion().getVersion());
        assertEquals(25, flyway.info().applied().length);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        // EntityManagerFactory 기동 시 전체 매핑 validate가 실행되며 이 조회도 실제 JPA를 사용한다.
        assertNotNull(members.findByEmail("missing-" + UUID.randomUUID() + "@example.test"));
    }

    private Browser csrfToken(MockHttpSession session) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Browser((MockHttpSession) result.getRequest().getSession(),
                mapper.readTree(result.getResponse().getContentAsString()).path("token").asString());
    }
    private MockHttpServletRequestBuilder postWithCsrf(String path, Browser browser) {
        return post(path).session(browser.session()).header("X-CSRF-TOKEN", browser.token()).contentType("application/json");
    }
    private String credentials(String email, String value) {
        return mapper.writeValueAsString(Map.of("email", email, "password", value));
    }
    private Browser registerAndLogin(String email) throws Exception {
        var browser = csrfToken(null);
        mvc.perform(postWithCsrf("/api/v1/auth/signup", browser).content(credentials(email, password)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/auth/me").session(browser.session())).andExpect(status().isUnauthorized());
        var stored = members.findByEmail(email).orElseThrow();
        assertTrue(encoder.matches(password, stored.getPasswordHash()));
        assertTrue(!password.equals(stored.getPasswordHash()), "Password must be hashed");
        assertEquals("ACTIVE", stored.getStatus());
        assertEquals("MEMBER", stored.getRole());
        mvc.perform(postWithCsrf("/api/v1/auth/signup", browser).content(credentials(" " + email.toUpperCase(Locale.ROOT) + " ", password)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        mvc.perform(postWithCsrf("/api/v1/auth/login", browser).content(credentials(email, "incorrect long password")))
                .andExpect(status().isUnauthorized());
        String oldId = browser.session().getId();
        mvc.perform(postWithCsrf("/api/v1/auth/login", browser).content(credentials(email.toUpperCase(Locale.ROOT), password)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberId").value(stored.getMemberId()));
        assertNotEquals(oldId, browser.session().getId());
        mvc.perform(get("/api/v1/auth/me").session(browser.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        return csrfToken(browser.session());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mens", "womens"})
    void realMembersCompleteImageFlowWhileCrossMemberReadsAndWritesAreDenied(String scope) throws Exception {
        String run = UUID.randomUUID().toString();
        String emailA = "mysql-a-" + run + "@example.test", emailB = "mysql-b-" + run + "@example.test";
        var a = registerAndLogin(emailA);
        var b = registerAndLogin(emailB);
        var started = mvc.perform(postWithCsrf("/api/v1/sessions/simple-image?apparelScope=" + scope, b))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apparelScope").value(scope)).andReturn();
        String id = mapper.readTree(started.getResponse().getContentAsString()).path("sessionId").asString();
        assertEquals(members.findByEmail(emailB).orElseThrow().getMemberId(), sessions.findById(id).orElseThrow().getMallMemberId());
        mvc.perform(get("/api/v1/sessions/active").session(b.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id));
        mvc.perform(get("/api/v1/sessions/" + id + "/questions").session(b.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions.length()").value(10));
        String[] payloads = {"{\"selectedOptionIds\":[\"A\"]}", "{\"selectedOptionIds\":[\"R01\"]}",
                "{\"selectedOptionIds\":[\"S02\"]}", "{\"selectedOptionIds\":[\"P01\"]}",
                "{\"selectedOptionId\":\"A\"}", "{\"selectedOptionId\":\"G\"}", "{\"selectedOptionIds\":[\"NONE\"]}",
                "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"A\"},{\"comparisonId\":\"C02\",\"selectedOptionId\":\"A\"},{\"comparisonId\":\"C03\",\"selectedOptionId\":\"B\"}]}",
                "{\"selections\":[{\"comparisonId\":\"C01\",\"selectedOptionId\":\"B\"},{\"comparisonId\":\"C02\",\"selectedOptionId\":\"E\"},{\"comparisonId\":\"C03\",\"selectedOptionId\":\"A\"}]}",
                "{\"selectedOptionId\":\"J\"}"};
        if ("womens".equals(scope)) {
            payloads[4] = "{\"selectedOptionIds\":[\"A\",\"B\"]}";
            payloads[5] = "{\"selectedOptionIds\":[\"H\"]}";
            payloads[7] = """
                    {"selections":[
                      {"comparisonId":"C01","selectedOptionId":"A"},
                      {"comparisonId":"C02","selectedOptionId":"B"},
                      {"comparisonId":"C03","selectedOptionId":"A"},
                      {"comparisonId":"C04","selectedOptionIds":["B","A"]},
                      {"comparisonId":"C05","selectedOptionIds":["D","B"]}]}
                    """;
            payloads[8] = """
                    {"selections":[
                      {"comparisonId":"C01","selectedOptionId":"A"},
                      {"comparisonId":"C02","selectedOptionIds":["E","B","A"]},
                      {"comparisonId":"C03","selectedOptionId":"B"},
                      {"comparisonId":"C04","selectedOptionId":"A"}]}
                    """;
            payloads[9] = "{\"selectedOptionIds\":[\"A\",\"S\"]}";
        }
        for (int i = 0; i < payloads.length; i++) {
            mvc.perform(postWithCsrf("/api/v1/sessions/" + id + "/answers", b).content(mapper.writeValueAsString(
                    Map.of("questionId", "Q%02d".formatted(i+1), "expectedRevision", i, "answerPayload", mapper.readTree(payloads[i])))))
                    .andExpect(status().isOk());
        }
        var saved = answers.findByIdSessionIdOrderByIdQuestionIdAsc(id);
        assertEquals(10, saved.size());
        for (int i = 0; i < 10; i++) assertEquals(mapper.readTree(payloads[i]), mapper.readTree(saved.get(i).getAnswerPayload()));
        // 수정 후 재개·재조회에서도 실제 저장된 선택 순서를 유지해야 한다.
        payloads[4] = "womens".equals(scope)
                ? "{\"selectedOptionIds\":[\"NONE\"]}" : "{\"selectedOptionId\":\"B\"}";
        mvc.perform(postWithCsrf("/api/v1/sessions/" + id + "/answers", b).content(mapper.writeValueAsString(
                Map.of("questionId", "Q05", "expectedRevision", 10, "answerPayload", mapper.readTree(payloads[4])))))
                .andExpect(status().isOk());
        var resumed = mvc.perform(postWithCsrf("/api/v1/sessions/" + id + "/resume", b))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(id)).andReturn();
        long revision = mapper.readTree(resumed.getResponse().getContentAsString()).path("revision").asLong();
        var restored = mvc.perform(get("/api/v1/sessions/" + id + "/answers").session(b.session()))
                .andExpect(status().isOk()).andReturn();
        var restoredAnswers = mapper.readTree(restored.getResponse().getContentAsString()).path("answers");
        for (int i = 0; i < 10; i++)
            assertEquals(mapper.readTree(payloads[i]), restoredAnswers.path("Q%02d".formatted(i + 1)));
        for (String suffix : List.of("", "/questions", "/answers", "/preference-summary"))
            mvc.perform(get("/api/v1/sessions/" + id + suffix).session(a.session())).andExpect(status().isNotFound());
        mvc.perform(postWithCsrf("/api/v1/sessions/" + id + "/answers", a)
                .content("{\"questionId\":\"Q10\",\"expectedRevision\":10,\"answerPayload\":{\"selectedOptionId\":\"A\"}}"))
                .andExpect(status().isNotFound());
        mvc.perform(postWithCsrf("/api/v1/sessions/" + id + "/submit", a).content("{\"expectedRevision\":10}"))
                .andExpect(status().isNotFound());
        mvc.perform(postWithCsrf("/api/v1/sessions/" + id + "/resume", a)).andExpect(status().isNotFound());
        mvc.perform(postWithCsrf("/api/v1/sessions/" + id + "/submit", b)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", revision))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(get("/api/v1/sessions/" + id + "/preference-summary").session(b.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DIRECT_PREFERENCE_SUMMARY"));
        mvc.perform(get("/api/v1/sessions/" + id + "/preference-summary").session(a.session())).andExpect(status().isNotFound());
        String matchingPath = "/api/v1/sessions/" + id + "/matched-demo-products";
        mvc.perform(get(matchingPath).session(a.session())).andExpect(status().isNotFound());
        String matching = mvc.perform(get(matchingPath).session(b.session())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(matching), mapper.readTree(mvc.perform(get(matchingPath).session(b.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
        var expectedIds = demoProducts.findMatchingStyles(Set.of("R01")).stream()
                .filter(p -> p.getApparelScope() == DemoProduct.ApparelScope.UNISEX
                        || p.getApparelScope() != null && p.getApparelScope().name().equalsIgnoreCase(scope))
                .map(DemoProduct::getProductId).sorted().toList();
        List<String> actualIds = new ArrayList<>();
        boolean hasColor = false;
        for (var product : mapper.readTree(matching).path("products")) {
            actualIds.add(product.path("id").asString());
            boolean hasStyle = false;
            for (var fact : product.path("evidence")) {
                if ("Q02".equals(fact.path("questionId").asString())) {
                    hasStyle = true;
                    assertEquals("R01", fact.path("styleCode").asString());
                } else {
                    assertEquals("Q01", fact.path("questionId").asString());
                    assertEquals("A", fact.path("colorCode").asString());
                    assertEquals("블랙", fact.path("colorLabel").asString());
                    hasColor = true;
                }
            }
            assertTrue(hasStyle);
        }
        assertEquals(expectedIds, actualIds); // 색상 불일치·미등록이어도 스타일 후보가 유지된다.
        assertFalse(actualIds.isEmpty());
        assertTrue(hasColor);
        mvc.perform(get("/api/v1/sessions/" + id + "/answers").session(b.session()))
                .andExpect(status().isOk());
        for (var browser : List.of(a, b)) {
            mvc.perform(postWithCsrf("/api/v1/auth/logout", browser)).andExpect(status().isNoContent());
            assertTrue(browser.session().isInvalid());
        }
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void mysqlUniqueConstraintRejectsConcurrentJpaInserts() throws Exception {
        String email = "mysql-race-" + UUID.randomUUID() + "@example.test";
        String hash = encoder.encode(password);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> insert = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timeout");
                try {
                    members.saveAndFlush(new FormMember("form-" + UUID.randomUUID(), email, hash, LocalDateTime.now()));
                    return true;
                } catch (DataIntegrityViolationException expected) { return false; }
            };
            var first = executor.submit(insert);
            var second = executor.submit(insert);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertTrue(members.findByEmail(email).isPresent());
        }
    }
}
