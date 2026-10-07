package com.muhjain.school.enquiry;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 8: an admission made from an enquiry closes that enquiry, in the same transaction. */
class AdmissionFromEnquiryTest extends EnquiryTestBase {

	private ResultActions admit(String enquiryId) throws Exception {
		String body = "{\"name\":\"Aryan Jain\",\"dob\":\"2018-05-14\",\"gender\":\"M\",\"className\":\"3\","
				+ "\"village\":\"Jakhal\",\"fatherOccupation\":\"OTHER\",\"guardians\":[{\"name\":\"Ramesh\","
				+ "\"phone\":\"9812340208\",\"relation\":\"FATHER\"}]" + ((enquiryId == null) ? "" : ",\"enquiryId\":" + enquiryId)
				+ "}";
		return mockMvc.perform(post("/api/v1/admissions").header("Authorization", bearer(desk))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	@Test
	void admissionWithEnquiryIdMarksItAdmitted() throws Exception {
		long id = idOf(create(desk, FIVE));
		move(desk, id, "{\"status\":\"APPLIED\"}").andExpect(status().isConflict()); // NEW → APPLIED is not by hand
		move(desk, id, "{\"status\":\"VISITED\"}");
		String studentId = String.valueOf(((Number) com.jayway.jsonpath.JsonPath
			.read(admit(String.valueOf(id)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
					"$.studentId")).longValue());
		read(desk, "/api/v1/enquiries/" + id).andExpect(jsonPath("$.status").value("ADMITTED"))
			.andExpect(jsonPath("$.admittedStudentId").value(Long.parseLong(studentId)))
			.andExpect(jsonPath("$.overdue").value(false));
		assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(1);
		// The same enquiry cannot admit a second child.
		admit(String.valueOf(id)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("ENQUIRY_ALREADY_ADMITTED"));
		assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(1);
	}

	@Test
	void unknownEnquiryStopsTheAdmissionAndSavesNoChild() throws Exception {
		admit("99999").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.enquiryId").exists());
		assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from admission_counter", Integer.class)).isZero();
	}

	@Test
	void admissionWithoutEnquiryIdStillWorks() throws Exception {
		long id = idOf(create(desk, FIVE));
		admit(null).andExpect(status().isCreated());
		read(desk, "/api/v1/enquiries/" + id).andExpect(jsonPath("$.status").value("NEW"));
	}

	@Test
	void lostEnquiryCanBeAdmittedWhenTheParentComesBack() throws Exception {
		long id = idOf(create(desk, FIVE));
		move(desk, id, "{\"status\":\"LOST\",\"lostReason\":\"far\"}");
		admit(String.valueOf(id)).andExpect(status().isCreated());
		read(desk, "/api/v1/enquiries/" + id).andExpect(jsonPath("$.status").value("ADMITTED"))
			.andExpect(jsonPath("$.lostReason").doesNotExist());
	}

}
