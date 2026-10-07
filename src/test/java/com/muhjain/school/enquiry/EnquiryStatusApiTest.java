package com.muhjain.school.enquiry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 3 through the URL. */
class EnquiryStatusApiTest extends EnquiryTestBase {

	private long newEnquiry() throws Exception {
		return idOf(create(desk, FIVE));
	}

	@Test
	void walkInCanSkipToVisited() throws Exception {
		long id = newEnquiry();
		move(desk, id, "{\"status\":\"VISITED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VISITED"));
		move(desk, id, "{\"status\":\"APPLIED\"}").andExpect(jsonPath("$.status").value("APPLIED"));
	}

	@Test
	void skippingTwoStagesOrGoingBackIsRefused() throws Exception {
		long id = newEnquiry();
		move(desk, id, "{\"status\":\"APPLIED\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("STATUS_CHANGE_NOT_ALLOWED"));
		move(desk, id, "{\"status\":\"VISITED\"}");
		move(desk, id, "{\"status\":\"NEW\"}").andExpect(status().isConflict());
	}

	@Test
	void lostNeedsAReason() throws Exception {
		long id = newEnquiry();
		move(desk, id, "{\"status\":\"LOST\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.lostReason").exists());
		move(desk, id, "{\"status\":\"LOST\",\"lostReason\":\"  \"}").andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("select status from enquiry", String.class)).isEqualTo("NEW");
		move(desk, id, "{\"status\":\"LOST\",\"lostReason\":\"Joined a school near home\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LOST"))
			.andExpect(jsonPath("$.lostReason").value("Joined a school near home"));
	}

	@Test
	void admittedCannotBeSetByHand() throws Exception {
		long id = newEnquiry();
		move(desk, id, "{\"status\":\"ADMITTED\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.status").exists());
		assertThat(jdbc.queryForObject("select status from enquiry", String.class)).isEqualTo("NEW");
	}

	@Test
	void lostEnquiryCanBeReopened() throws Exception {
		long id = newEnquiry();
		move(desk, id, "{\"status\":\"LOST\",\"lostReason\":\"far\"}");
		move(desk, id, "{\"status\":\"CONTACTED\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONTACTED"))
			.andExpect(jsonPath("$.lostReason").doesNotExist());
		// Not back to any other stage.
		move(desk, id, "{\"status\":\"LOST\",\"lostReason\":\"again\"}");
		move(desk, id, "{\"status\":\"VISITED\"}").andExpect(status().isConflict());
	}

	@Test
	void reopeningIsRefusedWhenAnotherOpenEnquiryHasTheSamePhoneAndClass() throws Exception {
		long old = newEnquiry();
		move(desk, old, "{\"status\":\"LOST\",\"lostReason\":\"far\"}");
		long fresh = newEnquiry();
		move(desk, old, "{\"status\":\"CONTACTED\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("ENQUIRY_EXISTS"))
			.andExpect(jsonPath("$.fields.enquiryId").value(String.valueOf(fresh)));
	}

	@Test
	void sameStageAgainChangesNothing() throws Exception {
		long id = newEnquiry();
		move(desk, id, "{\"status\":\"NEW\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"));
	}

	@Test
	void admittedEnquiryCannotBeMovedOrLost() throws Exception {
		long id = addEnquiry("+919812340208", "3", "NEW", "Jakhal", null);
		long student = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on) values ('T-1', 'Aryan', '2018-05-14', 'M', '3', 'Jakhal', 'OTHER', "
				+ "'2026-04-01') returning id", Long.class);
		jdbc.update("update enquiry set status = 'ADMITTED', admitted_student_id = ? where id = ?", student, id);
		move(desk, id, "{\"status\":\"LOST\",\"lostReason\":\"x\"}").andExpect(status().isConflict());
		move(desk, id, "{\"status\":\"CONTACTED\"}").andExpect(status().isConflict());
	}

	@Test
	void noTokenGives401AndTransportInchargeGets403() throws Exception {
		long id = newEnquiry();
		String transport = tokenFor(addUser("+919812340003", com.muhjain.school.user.Role.TRANSPORT_INCHARGE));
		move(transport, id, "{\"status\":\"CONTACTED\"}").andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/enquiries/" + id + "/status").contentType("application/json").content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
