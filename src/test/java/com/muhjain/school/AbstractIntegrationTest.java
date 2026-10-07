package com.muhjain.school;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * Before each test: the clock is set to the real "now", and users, codes, audit rows, vehicles, staff and routes are deleted.
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
			user.setStaffId(addStaff("Balwan", "ATTENDANT"));
		}
		return userRepository.saveAndFlush(user);
	}

	/**
	 * Adds a staff row straight into the database and returns its id.
	 * A DRIVER gets a licence that ends on 31 Dec 2030. Example: {@code addStaff("Jagdish", "DRIVER")}
	 */
	protected long addStaff(String name, String staffType) {
		return addStaff(name, staffType, "DRIVER".equals(staffType) ? LocalDate.of(2030, 12, 31) : null);
	}

	/** Same, with the licence end date given (only for a DRIVER). */
	protected long addStaff(String name, String staffType, LocalDate licenceValidTill) {
		boolean driver = "DRIVER".equals(staffType);
		return jdbc.queryForObject("insert into staff (name, phone, staff_type, licence_no, licence_valid_till) "
				+ "values (?, '+919811100000', ?, ?, ?) returning id", Long.class, name, staffType,
				driver ? "HR2620110012345" : null, driver ? licenceValidTill : null);
	}

	/** Adds a small van (14 seats, 30300.00 a month) straight into the database and returns its id. */
	protected long addVehicle(String name) {
		return addVehicle(name, 14);
	}

	/** Same, with the number of seats given. */
	protected long addVehicle(String name, int seats) {
		return jdbc.queryForObject("insert into vehicle (name, registration_no, vehicle_type, seats, monthly_cost, "
				+ "owned_by) values (?, ?, ?, ?, 30300.00, 'CONTRACTOR') returning id", Long.class, name,
				"REG " + name.toUpperCase(), (seats > 14) ? "MID_BUS" : "SMALL_VAN", seats);
	}

	/**
	 * Adds an assignment row straight into the database and returns its id.
	 * Example: {@code addAssignment(van4, jagdish, "DRIVER", "2026-04-01", null, false)}
	 */
	protected long addAssignment(long vehicleId, long staffId, String duty, String from, String to,
			boolean temporary) {
		return jdbc.queryForObject("insert into vehicle_assignment (vehicle_id, staff_id, duty, from_date, to_date, "
				+ "temporary) values (?, ?, ?, ?::date, ?::date, ?) returning id", Long.class, vehicleId, staffId,
				duty, from, to, temporary);
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
		// Children first, because of the foreign keys.
		jdbc.update("delete from boarding_event");
		jdbc.update("delete from message_outbox");
		jdbc.update("delete from transport_enrolment");
		jdbc.update("delete from student_guardian");
		jdbc.update("delete from guardian");
		jdbc.update("delete from student");
		jdbc.update("delete from admission_counter");
		jdbc.update("delete from route_stop");
		jdbc.update("delete from route");
		jdbc.update("delete from vehicle_assignment");
		jdbc.update("delete from vehicle_document");
		jdbc.update("update app_setting set updated_by = null");
		jdbc.update("delete from audit_log");
		jdbc.update("delete from otp_code");
		jdbc.update("delete from app_user");
		jdbc.update("delete from staff");
		jdbc.update("delete from vehicle");
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
