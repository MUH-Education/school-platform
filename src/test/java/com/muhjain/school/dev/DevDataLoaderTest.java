package com.muhjain.school.dev;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.service.dev.DevDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The loader, run in a context with the {@code dev} profile on. The test cleans the tables before each test, so
 * the loader is called by hand here. Today is 7 Oct 2026.
 * <p>
 * {@code app.bootstrap.owner-phone} is set so OwnerBootstrap (@Order(0)) makes a real OWNER before this
 * context's own, automatic startup run of the loader — the same order a real laptop boot has, needed
 * now that {@code vehicle_assignment.created_by} is not null.
 */
@ActiveProfiles({ "test", "local" })
@TestPropertySource(properties = "app.bootstrap.owner-phone=9999900000")
class DevDataLoaderTest extends AbstractIntegrationTest {

	@Autowired
	private DevDataLoader loader;

	private String token;

	@BeforeEach
	void load() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340001", Role.OWNER));
		loader.run(null);
	}

	private ResultActions read(String url) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", bearer(token)));
	}

	@Test
	void loadsNineVehiclesWithTheRightSizeAndCost() {
		assertThat(jdbc.queryForObject("select count(*) from vehicle", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("select count(*) from vehicle where vehicle_type = 'SMALL_VAN' "
				+ "and seats = 14", Integer.class))
			.isEqualTo(7);
		assertThat(jdbc.queryForObject("select count(*) from vehicle where vehicle_type = 'MID_BUS' "
				+ "and seats = 26", Integer.class))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from vehicle where monthly_cost = 30300.00",
				Integer.class))
			.isEqualTo(9);
		assertThat(jdbc.queryForObject("select sum(seats) from vehicle", Integer.class)).isEqualTo(150);
	}

	@Test
	void loadBoardShowsNineRoutesAnd150Seats() throws Exception {
		read("/api/v1/routes/load-board").andExpect(status().isOk())
			.andExpect(jsonPath("$.routes.length()").value(9))
			.andExpect(jsonPath("$.totals.routes").value(9))
			.andExpect(jsonPath("$.totals.vehicles").value(9))
			.andExpect(jsonPath("$.totals.seats").value(150))
			// Phase 3: 40 students, 35 with a bus. One of them starts the bus 25 days from now, so 34 ride today.
			.andExpect(jsonPath("$.totals.children").value(34));
	}

	@Test
	void everyRouteRunsItsOwnVehicleAndHasOrderedStops() throws Exception {
		read("/api/v1/routes").andExpect(jsonPath("$.length()").value(9))
			.andExpect(jsonPath("$[3].name").value("Route 4"))
			.andExpect(jsonPath("$[3].vehicle.name").value("Van 4"))
			.andExpect(jsonPath("$[3].stops.length()").value(4))
			.andExpect(jsonPath("$[3].stops[0].name").value("Sadhanwas"))
			.andExpect(jsonPath("$[3].stops[0].seqNo").value(1))
			.andExpect(jsonPath("$[3].stops[0].morningTime").value("07:10"))
			.andExpect(jsonPath("$[3].stops[1].morningTime").value("07:22"))
			.andExpect(jsonPath("$[8].vehicle.name").value("Bus 9"))
			.andExpect(jsonPath("$[8].vehicle.seats").value(26))
			.andExpect(jsonPath("$[8].stops[0].name").value("Model Town"));
	}

	@Test
	void everyVehicleHasADriverAndAnAttendantToday() throws Exception {
		read("/api/v1/vehicles").andExpect(jsonPath("$.length()").value(9))
			.andExpect(jsonPath("$[3].name").value("Van 4"))
			.andExpect(jsonPath("$[3].registrationNo").value("HR 23 A 1104"))
			.andExpect(jsonPath("$[3].driver.name").value("Jagdish"))
			.andExpect(jsonPath("$[3].attendant.name").value("Balwan"))
			.andExpect(jsonPath("$[3].route.name").value("Route 4"))
			.andExpect(jsonPath("$[0].driver.name").value("Rajpal"));
		assertThat(jdbc.queryForObject("select count(*) from vehicle_assignment", Integer.class)).isEqualTo(18);
	}

	@Test
	void spareDriverAndAttendantAreOnNoVehicle() throws Exception {
		assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class)).isEqualTo(20);
		read("/api/v1/staff").andExpect(jsonPath("$[18].name").value("Surender"))
			.andExpect(jsonPath("$[18].worksOn").isEmpty())
			.andExpect(jsonPath("$[19].name").value("Naresh"))
			.andExpect(jsonPath("$[19].worksOn").isEmpty());
	}

	@Test
	void attentionListIsNotEmpty() throws Exception {
		read("/api/v1/vehicles/attention").andExpect(jsonPath("$.length()").value(3))
			// Van 6 PUC ended 5 days ago, Van 2 insurance ends in 12 days, Dalbir's licence in 20 days.
			.andExpect(jsonPath("$[0].vehicleName").value("Van 6"))
			.andExpect(jsonPath("$[0].status").value("ENDED"))
			.andExpect(jsonPath("$[1].vehicleName").value("Van 2"))
			.andExpect(jsonPath("$[1].daysLeft").value(12))
			.andExpect(jsonPath("$[2].staffName").value("Dalbir"))
			.andExpect(jsonPath("$[2].daysLeft").value(20));
	}

	@Test
	void runningItAgainAddsNothing() {
		loader.run(null);

		assertThat(jdbc.queryForObject("select count(*) from vehicle", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class)).isEqualTo(20);
		assertThat(jdbc.queryForObject("select count(*) from route", Integer.class)).isEqualTo(9);
	}

	@Test
	void everythingLoadedIsInTheChangeHistory() {
		List<String> lines = jdbc.queryForList("select summary from audit_log where entity_type = 'VEHICLE' "
				+ "order by id limit 3", String.class);

		assertThat(lines.getFirst()).isEqualTo("Vehicle Van 1 (HR 23 A 1101) added");
	}

	@Test
	void loaderIsOnlyForTheLocalProfile() {
		Profile profile = DevDataLoader.class.getAnnotation(Profile.class);

		assertThat(profile).isNotNull();
		assertThat(profile.value()).containsExactly("local");
	}

	@Test
	void loadsTheNotificationData() {
		// 15 templates: 3 notices, 2 urgent, 3 marketing (approved / waiting / rejected), 2 greetings, 3 fee tones,
		// welcome (one family), staff.
		assertThat(jdbc.queryForList("select approval || ' ' || kind from message_template where kind <> 'SYSTEM'",
				String.class)).hasSize(15)
			.contains("WAITING MARKETING", "REJECTED MARKETING", "APPROVED GREETING", "APPROVED STAFF", "APPROVED URGENT",
					"APPROVED ONE_FAMILY");
		assertThat(jdbc.queryForObject("select count(*) from message_template where kind = 'FEE_REMINDER' "
				+ "and approval = 'APPROVED'", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select rejection_reason from message_template where approval = 'REJECTED'",
				String.class)).isNotBlank();
		assertThat(jdbc.queryForObject("select header_type from message_template where name = 'Diwali wishes'",
				String.class)).isEqualTo("VIDEO");
		assertThat(jdbc.queryForObject("select count(*) from message_template where provider_template_name is null "
				+ "and kind <> 'SYSTEM'", Integer.class)).isZero();
		// 3 families on the stop list, 4 replies (2 still waiting for a person).
		assertThat(jdbc.queryForObject("select count(*) from message_stop", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from inbound_message", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from inbound_message where not handled", Integer.class))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from inbound_message where promised_until is not null",
				Integer.class)).isEqualTo(1);
		// "Open day" was sent: one row per family, in mixed states; the stopped families are SKIPPED.
		long openDay = jdbc.queryForObject("select id from notification where kind = 'MARKETING'", Long.class);
		assertThat(jdbc.queryForObject("select status from notification where id = ?", String.class, openDay))
			.isEqualTo("SENT");
		List<String> states = jdbc.queryForList("select distinct status from message_outbox where notification_id = ?",
				String.class, openDay);
		assertThat(states).contains("DELIVERED", "TEST_ONLY", "FAILED", "SKIPPED");
		assertThat(jdbc.queryForObject("select count(*) from message_outbox where notification_id = ? "
				+ "and skip_reason = 'STOPPED'", Integer.class, openDay)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from message_outbox where notification_id = ? "
				+ "and fallback_of_id is not null", Integer.class, openDay)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from message_outbox where notification_id = ? "
				+ "and read_at is not null", Integer.class, openDay)).isPositive();
		assertThat(jdbc.queryForObject("select count(*) from message_outbox where button_action = 'BOOK_VISIT'",
				Integer.class)).isEqualTo(1);
		// One scheduled notice, one fee reminder draft (made by hand).
		assertThat(jdbc.queryForList("select status || ' ' || kind from notification where id <> ? and source = 'MANUAL' "
				+ "order by id", String.class, openDay)).containsExactly("SCHEDULED NOTICE", "DRAFT FEE_REMINDER");
		// Automatic messages: three on, every rule but the documents one has a template, one run by hand.
		assertThat(jdbc.queryForList("select code from notification_rule where enabled order by id", String.class))
			.containsExactly("FEE_REMINDER_10TH", "BIRTHDAY", "BUS_LATE");
		assertThat(jdbc.queryForList("select code from notification_rule where template_id is null", String.class))
			.containsExactly("DOCUMENTS_MISSING_MONDAY");
		assertThat(jdbc.queryForList("select r.code from notification n join notification_rule r on r.id = n.rule_id",
				String.class)).containsExactly("FEE_REMINDER_10TH");
		// Loading again adds nothing.
		loader.run(null);
		assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from message_template where kind <> 'SYSTEM'", Integer.class))
			.isEqualTo(15);
	}

}
