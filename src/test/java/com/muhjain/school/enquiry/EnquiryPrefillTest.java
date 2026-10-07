package com.muhjain.school.enquiry;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 6.9. */
class EnquiryPrefillTest extends EnquiryTestBase {

	@Test
	void prefillGivesTheFieldsTheAdmissionFormCopies() throws Exception {
		long id = idOf(create(desk, FIVE.replace("}", ",\"childName\":\"Aryan\",\"relation\":\"mother\","
				+ "\"needsBus\":\"YES\"}")));
		read(desk, "/api/v1/enquiries/" + id + "/prefill").andExpect(status().isOk())
			.andExpect(jsonPath("$.enquiryId").value(id))
			.andExpect(jsonPath("$.childName").value("Aryan"))
			.andExpect(jsonPath("$.className").value("3"))
			.andExpect(jsonPath("$.village").value("Jakhal"))
			.andExpect(jsonPath("$.needsBus").value("YES"))
			.andExpect(jsonPath("$.guardian.name").value("Ramesh Jain"))
			.andExpect(jsonPath("$.guardian.phone").value("+919812340208"))
			.andExpect(jsonPath("$.guardian.relation").value("MOTHER"))
			.andExpect(jsonPath("$.startedFrom").value("Started from the enquiry of Ramesh Jain (7 Oct 2026)"));
	}

	@Test
	void unknownRelationIsOtherAndMissingChildNameIsNull() throws Exception {
		long id = idOf(create(desk, FIVE.replace("}", ",\"relation\":\"neighbour\"}")));
		read(desk, "/api/v1/enquiries/" + id + "/prefill").andExpect(jsonPath("$.guardian.relation").value("OTHER"))
			.andExpect(jsonPath("$.childName").doesNotExist());
	}

	@Test
	void admittedEnquiryHasNothingToCopy() throws Exception {
		long id = idOf(create(desk, FIVE));
		long student = jdbc.queryForObject("insert into student (admission_no, name, dob, gender, class_name, village, "
				+ "father_occupation, joined_on) values ('T-1', 'Aryan', '2018-05-14', 'M', '3', 'Jakhal', 'OTHER', "
				+ "'2026-04-01') returning id", Long.class);
		jdbc.update("update enquiry set status = 'ADMITTED', admitted_student_id = ? where id = ?", student, id);
		read(desk, "/api/v1/enquiries/" + id + "/prefill").andExpect(status().isConflict());
		read(desk, "/api/v1/enquiries/99999/prefill").andExpect(status().isNotFound());
	}

	@Test
	void needsAdmissionsPermissionNotEnquiriesView() throws Exception {
		long id = idOf(create(desk, FIVE));
		read(office, "/api/v1/enquiries/" + id + "/prefill").andExpect(status().isOk());
		String transport = tokenFor(addUser("+919812340003", com.muhjain.school.user.Role.TRANSPORT_INCHARGE));
		read(transport, "/api/v1/enquiries/" + id + "/prefill").andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.get("/api/v1/enquiries/" + id + "/prefill")).andExpect(status().isUnauthorized());
	}

}
