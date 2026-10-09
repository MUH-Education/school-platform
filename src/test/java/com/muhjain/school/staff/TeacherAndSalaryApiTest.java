package com.muhjain.school.staff;

import java.time.Instant;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The tests of docs/phases/phase-10-staff-and-teachers.md. */
class TeacherAndSalaryApiTest extends AbstractIntegrationTest {

	private static final String SUNITA = """
			{"name":"Sunita","phone":"98123 40030","staffType":"TEACHER",
			 "details":{"joinedOn":"2024-04-01","dateOfBirth":"1990-07-12","gender":"FEMALE",
			            "address":"Ward 7,  Tohana","emergencyPhone":"098123 40099",
			            "idProofType":"AADHAAR","idProofLast4":"4321"},
			 "teaching":{"qualification":"B.Ed, M.A. Hindi","subjects":"Hindi, Social Science",
			             "classTeacherOf":"3"}}""";

	private String token;

	private String ownerToken;

	@BeforeEach
	void login() {
		clock.setInstant(Instant.parse("2026-10-07T04:30:00Z"));
		token = tokenFor(addUser("+919812340003", Role.TRANSPORT_INCHARGE));
		ownerToken = tokenFor(addUser("+919812340001", Role.OWNER));
	}

	private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
		return send(builder, body, token);
	}

	private ResultActions send(MockHttpServletRequestBuilder builder, String body, String who) throws Exception {
		MockHttpServletRequestBuilder request = builder.header("Authorization", bearer(who));
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private long addTeacher(String name, String classTeacherOf, String phoneTail) throws Exception {
		String teaching = (classTeacherOf == null) ? "{}" : "{\"classTeacherOf\":\"" + classTeacherOf + "\"}";
		String body = mockMvc
			.perform(post("/api/v1/staff").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"phone\":\"98123400" + phoneTail
						+ "\",\"staffType\":\"TEACHER\",\"teaching\":" + teaching + "}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return com.jayway.jsonpath.JsonPath.<Integer>read(body, "$.id").longValue();
	}

	@Test
	void teacherIsSavedWithTheOfficeFileAndTheTeachingFile() throws Exception {
		send(post("/api/v1/staff"), SUNITA).andExpect(status().isCreated())
			.andExpect(jsonPath("$.staffType").value("TEACHER"))
			// A teacher has no licence, and the licence fields stay empty.
			.andExpect(jsonPath("$.licenceNo").isEmpty())
			.andExpect(jsonPath("$.licenceStatus").isEmpty())
			.andExpect(jsonPath("$.details.joinedOn").value("2024-04-01"))
			.andExpect(jsonPath("$.details.gender").value("FEMALE"))
			// Two spaces become one, and the emergency phone is stored as +91XXXXXXXXXX.
			.andExpect(jsonPath("$.details.address").value("Ward 7, Tohana"))
			.andExpect(jsonPath("$.details.emergencyPhone").value("+919812340099"))
			.andExpect(jsonPath("$.details.idProofLast4").value("4321"))
			.andExpect(jsonPath("$.teaching.subjects").value("Hindi, Social Science"))
			.andExpect(jsonPath("$.teaching.classTeacherOf").value("3"));

		assertThat(jdbc.queryForObject("select count(*) from teacher_profile", Integer.class)).isOne();
	}

	@Test
	void aTeacherCanNeverBePutOnAVehicle() throws Exception {
		long sunita = addTeacher("Sunita", null, "30");
		long van4 = addVehicle("Van 4");

		send(post("/api/v1/vehicles/" + van4 + "/assignments"),
				"{\"duty\":\"DRIVER\",\"staffId\":" + sunita + ",\"fromDate\":\"2026-10-12\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("WRONG_STAFF_TYPE"));

		assertThat(jdbc.queryForObject("select count(*) from vehicle_assignment", Integer.class)).isZero();
	}

	@Test
	void aTeachingFileSentForADriverIs400AndNothingIsSaved() throws Exception {
		send(post("/api/v1/staff"), "{\"name\":\"Jagdish\",\"phone\":\"9812340010\",\"staffType\":\"DRIVER\","
				+ "\"licenceNo\":\"HR99\",\"licenceValidTill\":\"2029-03-31\","
				+ "\"teaching\":{\"subjects\":\"Hindi\"}}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("NOT_A_TEACHER"));

		assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from teacher_profile", Integer.class)).isZero();
	}

	@Test
	void changingATeacherToHelperRemovesTheTeachingFile() throws Exception {
		long sunita = addTeacher("Sunita", "3", "30");
		assertThat(jdbc.queryForObject("select count(*) from teacher_profile", Integer.class)).isOne();

		send(put("/api/v1/staff/" + sunita), "{\"name\":\"Sunita\",\"phone\":\"9812340030\","
				+ "\"staffType\":\"HELPER\",\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.staffType").value("HELPER"))
			.andExpect(jsonPath("$.teaching").isEmpty());

		assertThat(jdbc.queryForObject("select count(*) from teacher_profile", Integer.class)).isZero();
	}

	@Test
	void turningAHelperIntoATeacherStartsAnEmptyTeachingFile() throws Exception {
		long balwan = addStaff("Balwan", "HELPER");

		send(put("/api/v1/staff/" + balwan), "{\"name\":\"Balwan\",\"phone\":\"9811100000\","
				+ "\"staffType\":\"TEACHER\",\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.teaching").isNotEmpty())
			.andExpect(jsonPath("$.teaching.qualification").isEmpty());
	}

	@Test
	void oneClassHasOneClassTeacher() throws Exception {
		addTeacher("Sunita", "3", "30");

		send(post("/api/v1/staff"), "{\"name\":\"Rekha\",\"phone\":\"9812340031\",\"staffType\":\"TEACHER\","
				+ "\"teaching\":{\"classTeacherOf\":\"3\"}}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("CLASS_TEACHER_TAKEN"))
			.andExpect(jsonPath("$.message").value("Sunita is already the class teacher of 3."));
	}

	@Test
	void theSameTeacherKeepingTheSameClassIsFine() throws Exception {
		long sunita = addTeacher("Sunita", "3", "30");

		send(put("/api/v1/staff/" + sunita), "{\"name\":\"Sunita\",\"phone\":\"9812340030\","
				+ "\"staffType\":\"TEACHER\",\"active\":true,\"teaching\":{\"classTeacherOf\":\"3\"}}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.teaching.classTeacherOf").value("3"));
	}

	@Test
	void aClassThatIsNotOfThisSchoolIs400() throws Exception {
		send(post("/api/v1/staff"), "{\"name\":\"Rekha\",\"phone\":\"9812340031\",\"staffType\":\"TEACHER\","
				+ "\"teaching\":{\"classTeacherOf\":\"7th\"}}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields['teaching.classTeacherOf']").value("is not a class of this school"));
	}

	@Test
	void anIdProofNeedsBothTheTypeAndTheDigits() throws Exception {
		send(post("/api/v1/staff"), "{\"name\":\"Rekha\",\"phone\":\"9812340031\",\"staffType\":\"TEACHER\","
				+ "\"details\":{\"idProofType\":\"AADHAAR\"}}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.idProofType").isNotEmpty());
		assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class)).isZero();
	}

	@Test
	void aFullAadhaarNumberIsRefused() throws Exception {
		send(post("/api/v1/staff"), "{\"name\":\"Rekha\",\"phone\":\"9812340031\",\"staffType\":\"TEACHER\","
				+ "\"details\":{\"idProofType\":\"AADHAAR\",\"idProofLast4\":\"123412341234\"}}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"));
	}

	@Test
	void theTypeFilterLeavesTeachersOutOfTheTransportScreen() throws Exception {
		addStaff("Jagdish", "DRIVER");
		addTeacher("Sunita", null, "30");

		send(get("/api/v1/staff"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
		send(get("/api/v1/staff?type=DRIVER&type=ATTENDANT&type=HELPER"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Jagdish"));
		send(get("/api/v1/staff?type=TEACHER"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Sunita"));
	}

	@Test
	void theStaffListNeverCarriesASalary() throws Exception {
		long sunita = addTeacher("Sunita", null, "30");
		send(put("/api/v1/staff/" + sunita + "/salary"), "{\"monthlySalary\":18500}", ownerToken)
			.andExpect(status().isOk());

		// The transport in-charge may read the list and one person, and sees no salary in either answer.
		send(get("/api/v1/staff"), null).andExpect(status().isOk())
			.andExpect(content().string(not(containsStringIgnoringCase("salary"))));
		send(get("/api/v1/staff/" + sunita), null).andExpect(status().isOk())
			.andExpect(content().string(not(containsStringIgnoringCase("salary"))));
	}

	@Test
	void onlyTheOwnerSeesAndSetsASalary() throws Exception {
		long sunita = addTeacher("Sunita", null, "30");

		send(get("/api/v1/staff/" + sunita + "/salary"), null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"));
		send(put("/api/v1/staff/" + sunita + "/salary"), "{\"monthlySalary\":18500}")
			.andExpect(status().isForbidden());

		send(put("/api/v1/staff/" + sunita + "/salary"), "{\"monthlySalary\":18500}", ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.monthlySalary").value(18500.00));
		send(get("/api/v1/staff/" + sunita + "/salary"), null, ownerToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.staffId").value((int) sunita))
			.andExpect(jsonPath("$.monthlySalary").value(18500.00));
	}

	@Test
	void settingTheSalaryAgainReplacesTheNumber() throws Exception {
		long sunita = addTeacher("Sunita", null, "30");

		send(put("/api/v1/staff/" + sunita + "/salary"), "{\"monthlySalary\":18500}", ownerToken)
			.andExpect(status().isOk());
		send(put("/api/v1/staff/" + sunita + "/salary"), "{\"monthlySalary\":19500.50}", ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.monthlySalary").value(19500.50));

		assertThat(jdbc.queryForObject("select count(*) from staff_salary", Integer.class)).isOne();
		// The change is in the audit log, so "who raised it" can be answered later.
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'STAFF' "
				+ "and summary like 'Sunita: Salary%'", Integer.class)).isEqualTo(2);
	}

	@Test
	void noSalarySavedYetIs404() throws Exception {
		long sunita = addTeacher("Sunita", null, "30");

		send(get("/api/v1/staff/" + sunita + "/salary"), null, ownerToken).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("NOT_FOUND"));
		send(get("/api/v1/staff/999999/salary"), null, ownerToken).andExpect(status().isNotFound());
	}

	@Test
	void aNegativeSalaryIs400() throws Exception {
		long sunita = addTeacher("Sunita", null, "30");

		send(put("/api/v1/staff/" + sunita + "/salary"), "{\"monthlySalary\":-100}", ownerToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"));
	}

	@Test
	void noTokenIs401OnEverySalaryUrl() throws Exception {
		long sunita = addTeacher("Sunita", null, "30");

		mockMvc.perform(get("/api/v1/staff/" + sunita + "/salary"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
		mockMvc
			.perform(put("/api/v1/staff/" + sunita + "/salary").contentType(MediaType.APPLICATION_JSON)
				.content("{\"monthlySalary\":1}"))
			.andExpect(status().isUnauthorized());
	}

}
