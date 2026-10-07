package com.muhjain.school.enquiry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 4 and rule 5 (overdue) seen through the follow-ups. Today is 7 Oct 2026. */
class EnquiryFollowUpTest extends EnquiryTestBase {

	@Test
	void followUpMovesTheNextDate() throws Exception {
		long id = idOf(create(desk, FIVE.replace("}", ",\"nextFollowUpOn\":\"2026-10-10\"}")));
		followUp(desk, id, "{\"note\":\"Called. Will visit on Sunday.\",\"nextActionOn\":\"2026-10-12\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.nextFollowUpOn").value("2026-10-12"))
			.andExpect(jsonPath("$.followUps.length()").value(1))
			.andExpect(jsonPath("$.followUps[0].note").value("Called. Will visit on Sunday."));
		followUp(desk, id, "{\"note\":\"Visited school\",\"nextActionOn\":\"2026-10-20\"}")
			.andExpect(jsonPath("$.nextFollowUpOn").value("2026-10-20"))
			.andExpect(jsonPath("$.followUps.length()").value(2))
			.andExpect(jsonPath("$.followUps[0].note").value("Visited school"));
		assertThat(jdbc.queryForObject("select count(*) from enquiry_follow_up", Integer.class)).isEqualTo(2);
	}

	@Test
	void followUpWithoutDateClearsTheNextDate() throws Exception {
		long id = idOf(create(desk, FIVE.replace("}", ",\"nextFollowUpOn\":\"2026-10-10\"}")));
		followUp(desk, id, "{\"note\":\"Final call, they will come on their own\"}")
			.andExpect(jsonPath("$.nextFollowUpOn").doesNotExist());
	}

	@Test
	void followUpOfADifferentEnquiryDoesNotTouchOthers() throws Exception {
		long a = idOf(create(desk, json("9812340208", "3", "\"nextFollowUpOn\":\"2026-10-10\"")));
		long b = idOf(create(desk, json("9812340209", "3", "\"nextFollowUpOn\":\"2026-10-11\"")));
		followUp(desk, a, "{\"note\":\"x\",\"nextActionOn\":\"2026-10-15\"}");
		read(desk, "/api/v1/enquiries/" + b).andExpect(jsonPath("$.nextFollowUpOn").value("2026-10-11"))
			.andExpect(jsonPath("$.followUps.length()").value(0));
	}

	@Test
	void followUpRecordsWhoTypedIt() throws Exception {
		long id = idOf(create(desk, FIVE));
		jdbc.update("update app_user set name = 'Neelam' where phone = '+919812340004'");
		followUp(desk, id, "{\"note\":\"Called\"}");
		read(office, "/api/v1/enquiries/" + id).andExpect(jsonPath("$.followUps[0].createdBy").value("Neelam"));
	}

	@Test
	void blankNoteOrPastDateGive400() throws Exception {
		long id = idOf(create(desk, FIVE));
		followUp(desk, id, "{\"note\":\"  \"}").andExpect(status().isBadRequest());
		followUp(desk, id, "{\"note\":\"x\",\"nextActionOn\":\"2026-10-06\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.nextActionOn").exists());
		followUp(desk, id, "{\"note\":\"x\",\"nextActionOn\":\"2026-10-07\"}").andExpect(status().isCreated());
		followUp(desk, 99999, "{\"note\":\"x\"}").andExpect(status().isNotFound());
	}

	@Test
	void closedEnquiryGetsTheNoteButKeepsItsDate() throws Exception {
		long id = addEnquiry("+919812340208", "3", "LOST", "Jakhal", "2026-10-20");
		followUp(desk, id, "{\"note\":\"Called again\",\"nextActionOn\":\"2026-10-25\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.nextFollowUpOn").value("2026-10-20"));
	}

	@Test
	void enquiryWithPastDateIsOverdue() throws Exception {
		long id = idOf(create(desk, FIVE));
		jdbc.update("update enquiry set next_follow_up_on = '2026-10-05', status = 'CONTACTED' where id = ?", id);
		read(desk, "/api/v1/enquiries/" + id).andExpect(jsonPath("$.overdue").value(true))
			.andExpect(jsonPath("$.overdueSince").value("2026-10-05"));
		// Today's date is not overdue yet.
		jdbc.update("update enquiry set next_follow_up_on = '2026-10-07' where id = ?", id);
		read(desk, "/api/v1/enquiries/" + id).andExpect(jsonPath("$.overdue").value(false));
		// A follow-up with a new date clears it.
		jdbc.update("update enquiry set next_follow_up_on = '2026-10-05' where id = ?", id);
		followUp(desk, id, "{\"note\":\"Called\",\"nextActionOn\":\"2026-10-12\"}").andExpect(jsonPath("$.overdue").value(false));
	}

	@Test
	void admittedOrLostIsNeverOverdue() throws Exception {
		long lost = addEnquiry("+919812340208", "3", "LOST", "Jakhal", "2026-10-01");
		read(desk, "/api/v1/enquiries/" + lost).andExpect(jsonPath("$.overdue").value(false))
			.andExpect(jsonPath("$.overdueSince").doesNotExist());
	}

	@Test
	void transportInchargeGets403() throws Exception {
		String transport = tokenFor(addUser("+919812340003", com.muhjain.school.user.Role.TRANSPORT_INCHARGE));
		followUp(transport, 1, "{\"note\":\"x\"}").andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/enquiries/1/follow-ups").contentType("application/json").content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
