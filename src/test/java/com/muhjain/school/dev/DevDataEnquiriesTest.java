package com.muhjain.school.dev;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 6.11: the dev data has 29 enquiries across the stages. */
@ActiveProfiles({ "test", "dev" })
class DevDataEnquiriesTest extends AbstractIntegrationTest {

	@Autowired
	private DevDataLoader loader;

	private String token;

	@BeforeEach
	void load() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340001", Role.OWNER));
		loader.run(null);
	}

	@Test
	void summaryShows29EnquiriesWith14PercentAdmitted() throws Exception {
		mockMvc.perform(get("/api/v1/enquiries/summary").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(29))
			.andExpect(jsonPath("$.byStatus.NEW").value(7))
			.andExpect(jsonPath("$.byStatus.CONTACTED").value(6))
			.andExpect(jsonPath("$.byStatus.VISITED").value(5))
			.andExpect(jsonPath("$.byStatus.APPLIED").value(3))
			.andExpect(jsonPath("$.byStatus.ADMITTED").value(4))
			.andExpect(jsonPath("$.byStatus.LOST").value(4))
			.andExpect(jsonPath("$.overdue").value(5))
			.andExpect(jsonPath("$.admittedPercent").value(14))
			.andExpect(jsonPath("$.byVillage.length()").value(8));
	}

	@Test
	void admittedEnquiriesPointAtRealStudentsAndRunningAgainAddsNothing() {
		assertThat(jdbc.queryForObject("select count(*) from enquiry e join student s on s.id = e.admitted_student_id",
				Integer.class)).isEqualTo(4);
		loader.run(null);
		assertThat(jdbc.queryForObject("select count(*) from enquiry", Integer.class)).isEqualTo(29);
	}

}
