package com.tastedna.session;

import com.tastedna.definition.persistence.*;
import com.tastedna.scoring.persistence.OptionScoringRuleRepository;
import com.tastedna.session.application.TestSessionService;
import com.tastedna.session.api.dto.*;
import com.tastedna.session.persistence.TestSessionRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"member-auth", "test"})
@EnabledIfEnvironmentVariable(named = "RUN_WOMENS_MYSQL_TESTS", matches = "true")
@Import(WomensSharedDefinitionMysqlTest.SafeMigration.class)
class WomensSharedDefinitionMysqlTest {
    static final String WOMENS = "6acc799d-1348-4f34-b756-593e9f5bfd52";
    static final String MENS = "33333333-3333-4333-8333-333333333333";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        if (!"coql".equals(System.getenv("DB_USERNAME")) || System.getenv("DB_PASSWORD") == null)
            throw new IllegalStateException("Dedicated test credentials are required");
        String url = "jdbc:mysql://localhost:3306/tastedna_test?connectionTimeZone=UTC";
        p.add("spring.datasource.url", () -> url);
        p.add("spring.datasource.username", () -> "coql");
        p.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        p.add("spring.flyway.url", () -> url);
        p.add("spring.flyway.user", () -> "coql");
        p.add("spring.flyway.password", () -> System.getenv("DB_PASSWORD"));
        p.add("spring.flyway.clean-disabled", () -> true);
        p.add("spring.flyway.locations", () -> "classpath:db/migration");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class SafeMigration {
        @Bean FlywayMigrationStrategy safeMigration() {
            return flyway -> {
                // JPA 초기화 전 접속 대상 검증에만 JDBC를 사용한다. 정의 조회는 JPA로 검증한다.
                try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
                    assertEquals("tastedna_test", connection.getCatalog());
                    assertTrue(connection.getMetaData().getUserName().startsWith("coql@"));
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException("TEST_DATABASE_VERIFICATION_FAILED", e);
                }
                flyway.migrate();
            };
        }
    }
    @Autowired TestDefinitionRepository definitions;
    @Autowired QuestionDefinitionRepository questions;
    @Autowired OptionScoringRuleRepository rules;
    @Autowired TestSessionRepository sessions;
    @Autowired TestSessionService service;
    @Autowired JsonMapper mapper;
    @Autowired Flyway flyway;

    @Test void migratedDefinitionIsInactiveAndSharedQuestionsAndRulesMatchMens() {
        assertEquals("17", flyway.info().current().getVersion().getVersion());
        var definition = definitions.findById(WOMENS).orElseThrow();
        assertFalse(definition.isActive());
        assertEquals(10, definition.getQuestionCount());
        assertEquals("SIMPLE-WOMENS-IMAGE-V1", definition.getVersionCode());
        var rows = questions.findByIdDefinitionIdOrderByDisplayOrderAsc(WOMENS);
        assertEquals(4, rows.size());
        for (var row : rows) {
            String qid = row.getId().getQuestionId();
            var male = questions.findByIdDefinitionIdAndIdQuestionId(MENS, qid).orElseThrow();
            assertEquals(mapper.readTree(male.getQuestionPayload()), mapper.readTree(row.getQuestionPayload()));
            assertEquals(male.isRequired(), row.isRequired());
            assertEquals(male.getDisplayOrder(), row.getDisplayOrder());
            assertEquals(mapper.readTree(male.getDependencyPayload()), mapper.readTree(row.getDependencyPayload()));
            var femaleRules = rules.findByIdDefinitionIdAndIdQuestionId(WOMENS, qid);
            var maleRules = rules.findByIdDefinitionIdAndIdQuestionId(MENS, qid);
            assertEquals(maleRules.size(), femaleRules.size());
            for (var rule : femaleRules) {
                var source = maleRules.stream().filter(r -> r.getId().getOptionId().equals(rule.getId().getOptionId())).findFirst().orElseThrow();
                assertEquals(mapper.readTree(source.getObservationPayload()), mapper.readTree(rule.getObservationPayload()));
                assertEquals(mapper.readTree(source.getScoringPayload()), mapper.readTree(rule.getScoringPayload()));
            }
        }
        long before = sessions.count();
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.startImageSimple("verification-no-session", ApparelScope.womens)).getStatusCode().value());
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.start("verification-no-session", new StartSessionRequest(Mode.SIMPLE, ApparelScope.womens))).getStatusCode().value());
        assertEquals(before, sessions.count());
    }
}
