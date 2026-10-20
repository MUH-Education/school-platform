package com.muhjain.school;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.muhjain.school.service.auth.SessionService;
import com.muhjain.school.service.auth.impl.LogOtpSender;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.repository.AppUserRepository;
import com.muhjain.school.model.enums.Role;
import com.redis.testcontainers.RedisContainer;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that need the whole app, a real database and a real Redis.
 * One PostgreSQL container and one Redis container start once and are shared by every test class.
 * Before each test: the clock is set to the real "now", Redis is flushed, and users, sessions,
 * audit rows, vehicles, staff and routes are deleted.
 * Example: {@code class HealthEndpointTest extends AbstractIntegrationTest}
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.TestClockConfig.class)
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@ServiceConnection
	static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

	static {
		postgres.start();
		redis.start();
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
	protected SessionService sessionService;

	@Autowired
	protected StringRedisTemplate redisTemplate;

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
				+ "temporary, created_by) values (?, ?, ?, ?::date, ?::date, ?, ?) returning id", Long.class,
				vehicleId, staffId, duty, from, to, temporary, anyUserId());
	}

	// db-design makes several created_by columns (vehicle_assignment, student, transport_enrolment, ...)
	// not null. Tests that build a row straight in the database and do not care who made it can reuse
	// any existing user, or make one if there is not one yet.
	protected Long anyUserId() {
		List<Long> existing = jdbc.queryForList("select id from app_user order by id limit 1", Long.class);
		return !existing.isEmpty() ? existing.get(0)
				: jdbc.queryForObject("insert into app_user (phone, role) values ('+919800000000', 'OWNER') "
						+ "returning id", Long.class);
	}

	/** The last code LogOtpSender was asked to send to this phone. */
	protected String lastCodeSentTo(String phone) {
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(logOtpSender, atLeastOnce()).send(eq(phone), code.capture());
		return code.getValue();
	}

	/** A valid token for this user, with a real session behind it, made directly. */
	protected String tokenFor(AppUser user) {
		return sessionService.create(user, "10.0.0.1").token();
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
		redisTemplate.getConnectionFactory().getConnection().flushAll();
		// Children first, because of the foreign keys. academic_session has no is_current flag any more:
		// "current" is whichever row's dates cover today, so the seeded 2026-27 row just needs to stay.
		// TRUNCATE, not DELETE: fee_payment and family_credit_entry rows can never be changed or
		// deleted (a trigger stops it) — TRUNCATE skips that per-row trigger entirely.
		jdbc.execute("truncate table fee_payment, family_credit_entry");
		jdbc.update("delete from receipt");
		jdbc.update("delete from fee_due");
		jdbc.update("delete from fee_plan");
		// Keep the seeded row of the base session: like bus_sms_rule, nothing re-inserts it after a delete.
		jdbc.update("delete from session_fee_setting where session_id <> "
				+ "(select id from academic_session where name = '2026-27')");
		jdbc.update("delete from class_fee");
		jdbc.update("delete from number_counter");
		jdbc.update("delete from promotion_choice");
		jdbc.update("delete from promotion_run");
		jdbc.update("delete from enquiry_stage");
		jdbc.update("delete from enquiry_follow_up");
		jdbc.update("delete from enquiry");
		jdbc.update("delete from message_outbox");
		// bus_sms_rule is seeded reference data (like app_setting), not per-test rows: not deleted here.
		jdbc.update("delete from boarding_event");
		jdbc.update("delete from holiday");
		jdbc.update("delete from student_document_note");
		jdbc.update("delete from student_document");
		jdbc.update("delete from transfer_certificate");
		jdbc.update("delete from upload");
		jdbc.update("delete from admission_draft");
		jdbc.update("delete from transport_enrolment");
		jdbc.update("delete from student_guardian");
		jdbc.update("delete from guardian");
		jdbc.update("delete from student");
		jdbc.update("delete from academic_session where name <> '2026-27'");
		jdbc.update("delete from route_stop");
		jdbc.update("delete from route");
		jdbc.update("delete from vehicle_assignment");
		jdbc.update("delete from vehicle_document");
		// Before app_user: staff_salary.updated_by points at the user who typed the salary.
		jdbc.update("delete from teacher_profile");
		jdbc.update("delete from staff_salary");
		jdbc.update("update app_setting set updated_by = null");
		// message_template is seeded reference data (like bus_sms_rule): a test may approve one and
		// set updated_by, but the row itself is never deleted, so the pointer must be cleared here too.
		jdbc.update("update message_template set updated_by = null");
		jdbc.update("delete from audit_log");
		jdbc.update("delete from user_session");
		jdbc.update("delete from otp_whitelist");
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
