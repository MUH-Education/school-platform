package com.muhjain.school;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the whole app and a real database.
 * One PostgreSQL container starts once and is shared by every test class.
 * Before each test: the clock is set to the real "now", and users, codes and audit rows are deleted.
 * Example: {@code class HealthEndpointTest extends AbstractIntegrationTest}
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.TestClockConfig.class)
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	static {
		postgres.start();
	}

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void resetClockAndTables() {
		clock.setInstant(Instant.now());
		jdbc.update("update app_setting set updated_by = null");
		jdbc.update("delete from audit_log");
		jdbc.update("delete from otp_code");
		jdbc.update("delete from app_user");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(Instant.now(), ZoneId.of("Asia/Kolkata"));
		}

	}

}
