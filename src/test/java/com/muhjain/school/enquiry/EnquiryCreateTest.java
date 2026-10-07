package com.muhjain.school.enquiry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules 1, 2, 6 and 7. */
class EnquiryCreateTest extends EnquiryTestBase {

	@Test
	void enquiryCanBeSavedWithFiveFields() throws Exception {
		create(desk, FIVE).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.phone").value("+919812340208"))
			.andExpect(jsonPath("$.classSought").value("3"))
			.andExpect(jsonPath("$.needsBus").value("UNKNOWN"))
			.andExpect(jsonPath("$.sessionName").value("2027-28"))
			.andExpect(jsonPath("$.overdue").value(false))
			.andExpect(jsonPath("$.followUps.length()").value(0));
		assertThat(jdbc.queryForObject("select created_by from enquiry", Long.class)).isNotNull();
	}

	@Test
	void missingNeededFieldsGive400() throws Exception {
		create(desk, "{\"parentName\":\"Ramesh\",\"phone\":\"9812340208\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.village").exists())
			.andExpect(jsonPath("$.fields.classSought").exists())
			.andExpect(jsonPath("$.fields.source").exists());
	}

	@Test
	void badPhoneAndBadClassGive400() throws Exception {
		create(desk, json("12345", "3", null)).andExpect(status().isBadRequest());
		create(desk, json("9812340208", "7th", null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.classSought").exists());
		assertThat(jdbc.queryForObject("select count(*) from enquiry", Integer.class)).isZero();
	}

	@Test
	void classIsSavedInTheStoredSpelling() throws Exception {
		create(desk, json("9812340208", "lkg", null)).andExpect(jsonPath("$.classSought").value("LKG"));
	}

	@Test
	void samePhoneAndClassTwiceGives409() throws Exception {
		long first = idOf(create(desk, FIVE).andExpect(status().isCreated()));
		create(desk, FIVE.replace("98123 40208", "+91 9812340208")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("ENQUIRY_EXISTS"))
			.andExpect(jsonPath("$.fields.enquiryId").value(String.valueOf(first)));
		assertThat(jdbc.queryForObject("select count(*) from enquiry", Integer.class)).isEqualTo(1);
	}

	@Test
	void samePhoneOtherClassIsAllowedAndSoIsAClosedOne() throws Exception {
		long first = idOf(create(desk, json("9812340208", "3", null)));
		create(desk, json("9812340208", "5", null)).andExpect(status().isCreated());
		jdbc.update("update enquiry set status = 'LOST', lost_reason = 'far' where id = ?", first);
		create(desk, json("9812340208", "3", null)).andExpect(status().isCreated());
	}

	@Test
	void referralNeedsAName() throws Exception {
		create(desk, json("9812340208", "3", "\"source\":\"REFERRAL\"").replace("\"source\":\"WALK_IN\",", ""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.referredBy").exists());
		create(desk, json("9812340208", "3", "\"source\":\"REFERRAL\",\"referredBy\":\"Dharampal\"")
			.replace("\"source\":\"WALK_IN\",", "")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.referredBy").value("Dharampal"));
	}

	@Test
	void unknownReferredByGuardianIsRefused() throws Exception {
		create(desk, json("9812340208", "3", "\"referredByGuardianId\":99999")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.referredByGuardianId").exists());
		long guardian = jdbc.queryForObject("insert into guardian (name, phone) values ('G', '+919811100001') "
				+ "returning id", Long.class);
		create(desk, json("9812340208", "3", "\"referredByGuardianId\":" + guardian)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.referredByGuardianId").value(guardian));
	}

	@Test
	void updateChangesDetailsButNotTheStage() throws Exception {
		long id = idOf(create(desk, FIVE));
		change(desk, id, FIVE.replace("}", ",\"childName\":\"Aryan\",\"needsBus\":\"YES\",\"note\":\"line1\\nline2\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.childName").value("Aryan"))
			.andExpect(jsonPath("$.needsBus").value("YES"))
			.andExpect(jsonPath("$.note").value("line1\nline2"))
			.andExpect(jsonPath("$.status").value("NEW"));
		change(desk, 99999, FIVE).andExpect(status().isNotFound());
	}

	@Test
	void updateCannotMakeATwin() throws Exception {
		create(desk, json("9812340208", "3", null));
		long other = idOf(create(desk, json("9812340209", "3", null)));
		change(desk, other, json("9812340208", "3", null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("ENQUIRY_EXISTS"));
		// Saving an enquiry again with its own phone is fine.
		change(desk, other, json("9812340209", "3", "\"note\":\"x\"")).andExpect(status().isOk());
	}

	@Test
	void getShowsOneEnquiry() throws Exception {
		long id = idOf(create(office, FIVE));
		read(desk, "/api/v1/enquiries/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.parentName").value("Ramesh Jain"));
		read(desk, "/api/v1/enquiries/99999").andExpect(status().isNotFound());
	}

	@Test
	void transportInchargeAndAttendantGet403AndNoTokenGets401() throws Exception {
		String transport = tokenFor(addUser("+919812340003", com.muhjain.school.user.Role.TRANSPORT_INCHARGE));
		String attendant = tokenFor(addUser("+919812340011", com.muhjain.school.user.Role.ATTENDANT));
		for (String token : new String[] { transport, attendant }) {
			create(token, FIVE).andExpect(status().isForbidden());
			read(token, "/api/v1/enquiries/1").andExpect(status().isForbidden());
			change(token, 1, FIVE).andExpect(status().isForbidden());
		}
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/enquiries/1"))
			.andExpect(status().isUnauthorized());
	}

}
