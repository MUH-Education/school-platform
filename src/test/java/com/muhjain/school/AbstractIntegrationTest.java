package com.muhjain.school;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;

import com.jayway.jsonpath.JsonPath;
import com.muhjain.school.auth.JwtService;
import com.muhjain.school.auth.LogOtpSender;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.AppUserRepository;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

	@Autowired
	protected AppUserRepository userRepository;

	@Autowired
	protected JwtService jwtService;

	// A spy: the real LogOtpSender runs, and tests can read which code it was given.
	@MockitoSpyBean
	protected LogOtpSender logOtpSender;

	/** Adds a user straight into the database. Example: {@code addUser("+919812340002", Role.OFFICE_ADMIN)} */
	protected AppUser addUser(String phone, Role role) {
		AppUser user = new AppUser(phone, role);
		if (role == Role.ATTENDANT) {
			user.setStaffId(14L);
		}
		return userRepository.saveAndFlush(user);
	}

	/** The last code LogOtpSender was asked to send to this phone. */
	protected String lastCodeSentTo(String phone) {
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(logOtpSender, atLeastOnce()).send(eq(phone), code.capture());
		return code.getValue();
	}

	/** A valid token for this user, made directly. */
	protected String tokenFor(AppUser user) {
		return jwtService.issue(user).token();
	}

	/** Real login over HTTP: ask for a code, read it from LogOtpSender, verify. Returns the token. */
	protected String login(String phone) throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"" + phone + "\"}")).andExpect(status().isOk());
		String body = mockMvc
			.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\",\"otp\":\"" + lastCodeSentTo(phone) + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		// The next login of the same phone may not wait 60 seconds.
		clock.advance(Duration.ofMinutes(1));
		return JsonPath.read(body, "$.token");
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

	@BeforeEach
	void resetClockAndTables() {
		clock.setInstant(Instant.now().truncatedTo(ChronoUnit.MILLIS));
		clearInvocations(logOtpSender);
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
