package com.muhjain.school.enquiry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules 5 and 10. Today is 7 Oct 2026. Six enquiries across stages and villages. */
class EnquiryListTest extends EnquiryTestBase {

	@BeforeEach
	void addEnquiries() {
		addEnquiry("+919812340001", "3", "NEW", "Jakhal", null);
		addEnquiry("+919812340002", "3", "CONTACTED", "Jakhal", "2026-10-05"); // overdue
		addEnquiry("+919812340003", "5", "VISITED", "Kanheri", "2026-10-07"); // today: not overdue
		addEnquiry("+919812340004", "5", "APPLIED", "kanheri", "2026-09-30"); // overdue
		addEnquiry("+919812340005", "1", "LOST", "Dhand", "2026-09-01"); // lost: never overdue
		addEnquiry("+919812340006", "1", "NEW", "Dhand", "2026-10-20");
		jdbc.update("update enquiry set parent_name = 'Sunita Devi', child_name = 'Aryan' where phone = '+919812340003'");
	}

	@Test
	void listIsPagedNewestFirst() throws Exception {
		read(desk, "/api/v1/enquiries").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(6))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.items[0].phone").value("+919812340006"))
			.andExpect(jsonPath("$.items[5].phone").value("+919812340001"));
		read(desk, "/api/v1/enquiries?size=4&page=1").andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));
		read(desk, "/api/v1/enquiries?size=0").andExpect(status().isBadRequest());
		read(desk, "/api/v1/enquiries?page=-1").andExpect(status().isBadRequest());
	}

	@Test
	void filtersByStatusVillageAndSource() throws Exception {
		read(desk, "/api/v1/enquiries?status=NEW").andExpect(jsonPath("$.totalItems").value(2));
		read(desk, "/api/v1/enquiries?village=KANHERI").andExpect(jsonPath("$.totalItems").value(2));
		read(desk, "/api/v1/enquiries?source=REFERRAL").andExpect(jsonPath("$.totalItems").value(0));
		read(desk, "/api/v1/enquiries?source=WALK_IN&status=NEW&village=dhand").andExpect(jsonPath("$.totalItems").value(1));
		read(desk, "/api/v1/enquiries?status=NOPE").andExpect(status().isBadRequest());
	}

	@Test
	void overdueFilterAndFlagOnEachRow() throws Exception {
		read(desk, "/api/v1/enquiries?overdue=true").andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.items[0].phone").value("+919812340004"))
			.andExpect(jsonPath("$.items[0].overdue").value(true))
			.andExpect(jsonPath("$.items[1].phone").value("+919812340002"))
			.andExpect(jsonPath("$.items[1].overdueSince").value("2026-10-05"));
		read(desk, "/api/v1/enquiries?overdue=false").andExpect(jsonPath("$.totalItems").value(6));
		read(desk, "/api/v1/enquiries").andExpect(jsonPath("$.items[?(@.phone=='+919812340005')].overdue").value(false))
			.andExpect(jsonPath("$.items[?(@.phone=='+919812340003')].overdue").value(false));
	}

	@Test
	void searchByNameOrPhone() throws Exception {
		read(desk, "/api/v1/enquiries?q=sunita").andExpect(jsonPath("$.totalItems").value(1));
		read(desk, "/api/v1/enquiries?q=ARYAN").andExpect(jsonPath("$.totalItems").value(1));
		read(desk, "/api/v1/enquiries?q=98123 40004").andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].phone").value("+919812340004"));
		read(desk, "/api/v1/enquiries?q=40004").andExpect(jsonPath("$.totalItems").value(1));
		read(desk, "/api/v1/enquiries?q=zzz").andExpect(jsonPath("$.totalItems").value(0));
		// A % in the search is just a character, not "everything".
		read(desk, "/api/v1/enquiries?q=%25").andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void listIsInTheOfficeAndAdmissionsDeskButNotTheTransportIncharge() throws Exception {
		read(office, "/api/v1/enquiries").andExpect(status().isOk());
		read(owner, "/api/v1/enquiries").andExpect(status().isOk());
		String transport = tokenFor(addUser("+919812340033", com.muhjain.school.user.Role.TRANSPORT_INCHARGE));
		read(transport, "/api/v1/enquiries").andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/enquiries"))
			.andExpect(status().isUnauthorized());
	}

}
