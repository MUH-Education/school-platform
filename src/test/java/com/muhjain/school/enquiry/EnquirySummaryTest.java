package com.muhjain.school.enquiry;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 9. Today is 7 Oct 2026. */
class EnquirySummaryTest extends EnquiryTestBase {

	private long student;

	private void admitted(String phone, String village) {
		long id = addEnquiry(phone, "3", "NEW", village, null);
		if (student == 0) {
			student = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
					+ "father_occupation, joined_on) values ('T-1', 'Aryan', '2018-05-14', 'M', '3', 'Jakhal', "
					+ "'OTHER', '2026-04-01') returning id", Long.class);
		}
		jdbc.update("update enquiry set status = 'ADMITTED', admitted_student_id = ? where id = ?", student, id);
	}

	@Test
	void summaryCountsEveryStage() throws Exception {
		addEnquiry("+919812340001", "3", "NEW", "Jakhal", null);
		addEnquiry("+919812340002", "3", "NEW", "Jakhal", "2026-10-05"); // overdue
		addEnquiry("+919812340003", "3", "CONTACTED", "Kanheri", "2026-10-06"); // overdue
		addEnquiry("+919812340004", "3", "VISITED", "kanheri", null);
		addEnquiry("+919812340005", "3", "APPLIED", "Dhand", "2026-10-08");
		addEnquiry("+919812340006", "3", "LOST", "Dhand", "2026-09-01"); // lost: not overdue
		admitted("+919812340007", "Jakhal");
		read(desk, "/api/v1/enquiries/summary").andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(7))
			.andExpect(jsonPath("$.byStatus.NEW").value(2))
			.andExpect(jsonPath("$.byStatus.CONTACTED").value(1))
			.andExpect(jsonPath("$.byStatus.VISITED").value(1))
			.andExpect(jsonPath("$.byStatus.APPLIED").value(1))
			.andExpect(jsonPath("$.byStatus.ADMITTED").value(1))
			.andExpect(jsonPath("$.byStatus.LOST").value(1))
			.andExpect(jsonPath("$.overdue").value(2))
			.andExpect(jsonPath("$.admitted").value(1))
			.andExpect(jsonPath("$.admittedPercent").value(14))
			.andExpect(jsonPath("$.byVillage.length()").value(3))
			// Kanheri and kanheri are one village; Jakhal has 3, Kanheri 2, Dhand 2.
			.andExpect(jsonPath("$.byVillage[0].village").value("Jakhal"))
			.andExpect(jsonPath("$.byVillage[0].count").value(3))
			.andExpect(jsonPath("$.byVillage[1].count").value(2));
	}

	@Test
	void emptySummaryHasZerosAndNoDivisionByZero() throws Exception {
		read(desk, "/api/v1/enquiries/summary").andExpect(jsonPath("$.total").value(0))
			.andExpect(jsonPath("$.byStatus.NEW").value(0))
			.andExpect(jsonPath("$.admittedPercent").value(0))
			.andExpect(jsonPath("$.byVillage.length()").value(0));
	}

	@Test
	void twentyNineEnquiriesWithFourAdmittedIs14Percent() throws Exception {
		for (int i = 0; i < 25; i++) {
			addEnquiry("+91981235" + String.format("%04d", i), "3", "NEW", "Jakhal", null);
		}
		for (int i = 0; i < 4; i++) {
			admitted("+91981236" + String.format("%04d", i), "Jakhal");
		}
		read(desk, "/api/v1/enquiries/summary").andExpect(jsonPath("$.total").value(29))
			.andExpect(jsonPath("$.admittedPercent").value(14));
	}

	@Test
	void transportInchargeGets403() throws Exception {
		String transport = tokenFor(addUser("+919812340003", com.muhjain.school.user.Role.TRANSPORT_INCHARGE));
		read(transport, "/api/v1/enquiries/summary").andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/enquiries/summary"))
			.andExpect(status().isUnauthorized());
	}

}
