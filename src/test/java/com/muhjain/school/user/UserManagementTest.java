package com.muhjain.school.user;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The "Users" tests of docs/phases/phase-1-login-users.md. */
class UserManagementTest extends AbstractIntegrationTest {

	private AppUser owner;

	private String ownerToken;

	@BeforeEach
	void addOwner() {
		owner = addUser("+919812340001", Role.OWNER);
		ownerToken = tokenFor(owner);
	}

	@Test
	void ownerCanAddUserWithPhoneAndRoleOnly() throws Exception {
		create(ownerToken, "{\"phone\":\"98123 40002\",\"role\":\"OFFICE_ADMIN\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.phone").value("+919812340002"))
			.andExpect(jsonPath("$.role").value("OFFICE_ADMIN"))
			.andExpect(jsonPath("$.name").isEmpty())
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.createdBy").value(owner.getId()));

		AppUser saved = userRepository.findByPhone("+919812340002").orElseThrow();
		assertThat(jdbc.queryForObject(
				"select summary from audit_log where entity_type = 'USER' and entity_id = ? and action = 'CREATED' "
						+ "and changed_by = ?",
				String.class, saved.getId(), owner.getId()))
			.isEqualTo("User +919812340002 added as OFFICE_ADMIN");

		mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(ownerToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[1].phone").value("+919812340002"));
	}

	@Test
	void officeAdminCannotAddUser() throws Exception {
		String adminToken = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));

		create(adminToken, "{\"phone\":\"98123 40003\",\"role\":\"OWNER\"}").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"))
			.andExpect(jsonPath("$.fields").isEmpty());
		assertThat(userRepository.findByPhone("+919812340003")).isEmpty();
	}

	@Test
	void duplicatePhoneGives409() throws Exception {
		addUser("+919812340002", Role.OFFICE_ADMIN);

		create(ownerToken, "{\"phone\":\"09812340002\",\"role\":\"ADMISSIONS_DESK\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("PHONE_ALREADY_USED"));

		AppUser other = addUser("+919812340003", Role.OFFICE_ADMIN);
		update(ownerToken, other.getId(), "{\"phone\":\"9812340002\",\"role\":\"OFFICE_ADMIN\",\"active\":true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("PHONE_ALREADY_USED"));
	}

	@Test
	void lastOwnerCannotBeTurnedOff() throws Exception {
		AppUser secondOwner = addUser("+919812340009", Role.OWNER);
		String secondOwnerToken = tokenFor(secondOwner);

		// Two owners: the second one may turn off the first one.
		update(secondOwnerToken, owner.getId(), "{\"phone\":\"+919812340001\",\"role\":\"OWNER\",\"active\":false}")
			.andExpect(status().isOk());

		// Now there is one active owner. Nobody can turn it off or give it another role.
		AppUser admin = addUser("+919812340002", Role.OFFICE_ADMIN);
		admin.setRole(Role.OWNER);
		userRepository.saveAndFlush(admin);
		admin.setActive(false);
		userRepository.saveAndFlush(admin);
		update(secondOwnerToken, secondOwner.getId(),
				"{\"phone\":\"+919812340009\",\"role\":\"OFFICE_ADMIN\",\"active\":true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("LAST_OWNER"));
		assertThat(userRepository.findById(secondOwner.getId()).orElseThrow().getRole()).isEqualTo(Role.OWNER);
	}

	@Test
	void userCannotTurnOffSelf() throws Exception {
		addUser("+919812340009", Role.OWNER);

		update(ownerToken, owner.getId(), "{\"phone\":\"+919812340001\",\"role\":\"OWNER\",\"active\":false}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("CANNOT_DISABLE_SELF"));
	}

	@Test
	void attendantUserNeedsStaffId() throws Exception {
		long staffId = addStaff("Balwan", "ATTENDANT");
		create(ownerToken, "{\"phone\":\"98123 40004\",\"role\":\"ATTENDANT\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.staffId").value("is required for an attendant"));

		create(ownerToken, "{\"phone\":\"98123 40004\",\"role\":\"OFFICE_ADMIN\",\"staffId\":" + staffId + "}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.staffId").value("is only for an attendant"));

		create(ownerToken, "{\"phone\":\"98123 40004\",\"role\":\"ATTENDANT\",\"name\":\"Balwan\",\"staffId\":" + staffId + "}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.staffId").value(staffId));
	}

	@Test
	void attendantUserMustPointAtARealStaffMemberOfTypeAttendant() throws Exception {
		long driver = addStaff("Jagdish", "DRIVER");
		long helper = addStaff("Rajpal", "HELPER");
		long attendant = addStaff("Balwan", "ATTENDANT");

		// No such staff row.
		create(ownerToken, "{\"phone\":\"98123 40004\",\"role\":\"ATTENDANT\",\"staffId\":999999}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.staffId").value("does not exist"));
		// A driver and a helper are staff, but not attendants.
		create(ownerToken, "{\"phone\":\"98123 40004\",\"role\":\"ATTENDANT\",\"staffId\":" + driver + "}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.staffId").value("must be a staff member of type ATTENDANT"));
		create(ownerToken, "{\"phone\":\"98123 40004\",\"role\":\"ATTENDANT\",\"staffId\":" + helper + "}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.staffId").value("must be a staff member of type ATTENDANT"));
		assertThat(userRepository.findByPhone("+919812340004")).isEmpty();

		create(ownerToken, "{\"phone\":\"98123 40004\",\"role\":\"ATTENDANT\",\"staffId\":" + attendant + "}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.staffId").value(attendant));
	}

	@Test
	void changingAnAttendantUserToAWrongStaffMemberIsRefused() throws Exception {
		AppUser balwan = addUser("+919812340004", Role.ATTENDANT);
		long driver = addStaff("Jagdish", "DRIVER");

		update(ownerToken, balwan.getId(), "{\"phone\":\"+919812340004\",\"role\":\"ATTENDANT\",\"staffId\":"
				+ driver + ",\"active\":true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.staffId").value("must be a staff member of type ATTENDANT"));
		update(ownerToken, balwan.getId(),
				"{\"phone\":\"+919812340004\",\"role\":\"ATTENDANT\",\"staffId\":999999,\"active\":true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.staffId").value("does not exist"));
		assertThat(userRepository.findById(balwan.getId()).orElseThrow().getStaffId()).isEqualTo(balwan.getStaffId());

		// Another attendant staff member is fine.
		long other = addStaff("Hari", "ATTENDANT");
		update(ownerToken, balwan.getId(), "{\"phone\":\"+919812340004\",\"role\":\"ATTENDANT\",\"staffId\":"
				+ other + ",\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.staffId").value(other));
	}

	@Test
	void changingPhoneRoleOrTurningOffEndsOldTokensAndIsAudited() throws Exception {
		AppUser admin = addUser("+919812340002", Role.OFFICE_ADMIN);
		int version = admin.getTokenVersion();

		update(ownerToken, admin.getId(),
				"{\"phone\":\"+919812340002\",\"name\":\"Neelam\",\"role\":\"OFFICE_ADMIN\",\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Neelam"));
		assertThat(userRepository.findById(admin.getId()).orElseThrow().getTokenVersion())
			.as("a name change keeps the token").isEqualTo(version);

		update(ownerToken, admin.getId(),
				"{\"phone\":\"+919812340012\",\"name\":\"Neelam\",\"role\":\"OFFICE_ADMIN\",\"active\":true}")
			.andExpect(status().isOk());
		assertThat(userRepository.findById(admin.getId()).orElseThrow().getTokenVersion()).isEqualTo(version + 1);

		update(ownerToken, admin.getId(),
				"{\"phone\":\"+919812340012\",\"name\":\"Neelam\",\"role\":\"OFFICE_ADMIN\",\"active\":false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		assertThat(userRepository.findById(admin.getId()).orElseThrow().getTokenVersion()).isEqualTo(version + 2);

		assertThat(jdbc.queryForList(
				"select summary from audit_log where entity_id = ? and action = 'UPDATED' order by id", String.class,
				admin.getId()))
			.containsExactly("Name changed from null to Neelam.", "Phone changed from +919812340002 to +919812340012.",
					"Turned off.");
	}

	@Test
	void unknownUserGives404() throws Exception {
		update(ownerToken, 999_999L, "{\"phone\":\"+919812340002\",\"role\":\"OFFICE_ADMIN\",\"active\":true}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void unknownRoleIsValidation() throws Exception {
		create(ownerToken, "{\"phone\":\"98123 40002\",\"role\":\"PRINCIPAL\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"));
	}

	private ResultActions create(String token, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/users").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions update(String token, Long id, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/users/" + id).header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

}
