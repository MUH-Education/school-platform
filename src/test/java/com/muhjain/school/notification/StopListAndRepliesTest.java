package com.muhjain.school.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.HandleInboundRequest;
import com.muhjain.school.model.dto.request.NotificationRequest;
import com.muhjain.school.model.dto.request.SendNotificationRequest;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.service.notification.AudienceService;
import com.muhjain.school.service.notification.InboundMessageService;
import com.muhjain.school.service.notification.NotificationDispatcher;
import com.muhjain.school.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 11.15: the stop list and the replies list, on the small school of {@link NotificationTestBase}: Punia
 * +919811100001, Chauhan +919811100002 (on the stop list), Sharma +919811100004 (owes fees), the enquiry Suresh
 * +919811100005. 7 Oct 2026, 10 am.
 */
class StopListAndRepliesTest extends NotificationTestBase {

	@Autowired
	private AudienceService audienceService;

	@Autowired
	private InboundMessageService inboundMessages;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationDispatcher dispatcher;

	private AppUser owner;

	private String token;

	@BeforeEach
	void owner() {
		owner = addUser("+919812340001", Role.OWNER);
		jdbc.update("update app_user set name = 'Neelam' where id = ?", owner.getId());
		token = tokenFor(owner);
	}

	private ResultActions call(MockHttpServletRequestBuilder builder, String json) throws Exception {
		if (json != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(builder.header("Authorization", bearer(token)));
	}

	private long reply(String phone, String text, Long guardianId, Long enquiryId, Duration ago) {
		return jdbc.queryForObject("insert into inbound_message (phone, text, provider_ref, received_at, guardian_id, "
				+ "enquiry_id) values (?, ?, ?, ?, ?, ?) returning id", Long.class, phone, text, "wamid." + phone + ago,
				Timestamp.from(clock.instant().minus(ago)), guardianId, enquiryId);
	}

	private boolean marketingReaches(String phone) {
		return audienceService.resolve(NotificationKind.MARKETING,
				new Audience(List.of(AudienceGroup.CURRENT_PARENTS), null))
			.recipients()
			.stream()
			.anyMatch(r -> phone.equals(r.phone()));
	}

	// ---------------------------------------------------------------- stop list

	@Test
	void theStopListShowsWhoWithAMaskedPhone() throws Exception {
		call(get("/api/v1/message-stops"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].guardianId").value(chauhan))
			.andExpect(jsonPath("$.items[0].name").value("Vikram Chauhan"))
			.andExpect(jsonPath("$.items[0].phone").value("+91XXXXXX0002"))
			.andExpect(jsonPath("$.items[0].reason").value("WROTE_STOP"));
	}

	@Test
	void aFamilyCanBeTakenOffTheStopList() throws Exception {
		assertThat(marketingReaches("+919811100002")).isFalse();
		call(delete("/api/v1/message-stops/" + chauhan), null).andExpect(status().isNoContent());
		assertThat(marketingReaches("+919811100002")).isTrue();
		assertThat(jdbc.queryForObject("select summary from audit_log where entity_type = 'MESSAGE_STOP' "
				+ "and action = 'DELETED'", String.class))
			.isEqualTo("+91XXXXXX0002 is off the stop list: marketing and greetings reach them again.");
		call(delete("/api/v1/message-stops/" + chauhan), null).andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- replies

	@Test
	void repliesNewestFirstWithWhoWroteThem() throws Exception {
		reply("+919811100004", "Fees next week", sharma, null, Duration.ofHours(3));
		reply("+919811100005", "When is the visit?", null, jdbc.queryForObject(
				"select id from enquiry where phone = '+919811100005'", Long.class), Duration.ofHours(1));
		long done = reply("+919811100001", "Thank you", punia, null, Duration.ofHours(2));
		jdbc.update("update inbound_message set handled = true where id = ?", done);

		call(get("/api/v1/inbound-messages?handled=false"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].text").value("When is the visit?"))
			.andExpect(jsonPath("$.items[0].fromType").value("ENQUIRY"))
			.andExpect(jsonPath("$.items[0].fromName").value("Suresh"))
			.andExpect(jsonPath("$.items[1].fromType").value("GUARDIAN"))
			.andExpect(jsonPath("$.items[1].fromName").value("Anil Sharma"))
			// The office may need to call back: the full phone.
			.andExpect(jsonPath("$.items[1].phone").value("+919811100004"));
		call(get("/api/v1/inbound-messages"), null).andExpect(jsonPath("$.totalItems").value(3));
	}

	@Test
	void feeReminderSkipsPromisedFamilyUntilPromiseDate() throws Exception {
		long id = reply("+919811100004", "शुक्रवार तक जमा कर देंगे", sharma, null, Duration.ofMinutes(30));
		call(post("/api/v1/inbound-messages/" + id + "/handle"), "{\"note\":\"Will pay on Friday\","
				+ "\"promisedUntil\":\"2026-10-09\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.handled").value(true))
			.andExpect(jsonPath("$.handledByName").value("Neelam"))
			.andExpect(jsonPath("$.note").value("Will pay on Friday"))
			.andExpect(jsonPath("$.promisedUntil").value("2026-10-09"));
		assertThat(jdbc.queryForObject("select summary from audit_log where entity_type = 'INBOUND_MESSAGE'",
				String.class)).isEqualTo("Reply from +91XXXXXX0004 handled: promised to pay by 2026-10-09.");

		// The fee reminder of today skips them, with the reason in the report.
		long templateId = jdbc.queryForObject("insert into message_template (kind, name, channel, body, approved_body, "
				+ "approval) values ('FEE_REMINDER', 'T', 'WHATSAPP', '{Amount due}', '{Amount due}', 'APPROVED') "
				+ "returning id", Long.class);
		long reminder = notificationService.create(new NotificationRequest("Fees", NotificationKind.FEE_REMINDER,
				templateId, null, null, Map.of(), null, new Audience(List.of(AudienceGroup.FAMILIES_WITH_DUES), null)),
				Role.OWNER.permissions(), owner.getId()).id();
		notificationService.send(reminder, new SendNotificationRequest(true, null), Role.OWNER.permissions(),
				owner.getId());
		dispatcher.runOnce();
		assertThat(jdbc.queryForObject("select skip_reason from message_outbox where notification_id = ? "
				+ "and phone = '+919811100004'", String.class, reminder)).isEqualTo("PROMISED");
	}

	@Test
	void promisedUntilMustBeSoon() throws Exception {
		long id = reply("+919811100004", "Next month", sharma, null, Duration.ofMinutes(5));
		call(post("/api/v1/inbound-messages/" + id + "/handle"), "{\"promisedUntil\":\"2026-10-06\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.promisedUntil").value("must be from today to 90 days ahead"));
		call(post("/api/v1/inbound-messages/" + id + "/handle"), "{\"promisedUntil\":\"2027-03-01\"}")
			.andExpect(status().isBadRequest());
		call(post("/api/v1/inbound-messages/999999/handle"), "{}").andExpect(status().isNotFound());
	}

	@Test
	void aReplyCanBecomeAnEnquiry() throws Exception {
		long id = reply("+919811100006", "Admission for my daughter?", null, null, Duration.ofMinutes(5));
		call(post("/api/v1/inbound-messages/" + id + "/handle"), "{\"makeEnquiry\":true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.enquiry").exists());
		call(post("/api/v1/inbound-messages/" + id + "/handle"), "{\"makeEnquiry\":true,\"enquiry\":{\"parentName\":"
				+ "\"Rakesh Goyal\",\"childName\":\"Isha\",\"classSought\":\"1\",\"village\":\"Tohana\"}}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fromType").value("ENQUIRY"))
			.andExpect(jsonPath("$.fromName").value("Rakesh Goyal"));
		assertThat(jdbc.queryForMap("select phone, source, child_name, status from enquiry where parent_name = "
				+ "'Rakesh Goyal'")).containsEntry("phone", "+919811100006")
			.containsEntry("source", "WHATSAPP")
			.containsEntry("child_name", "Isha")
			.containsEntry("status", "NEW");
	}

	@Test
	void anEnquiryNeedsTheEnquiryPermissionAndAnIndianPhone() {
		long indian = reply("+919811100007", "Admission?", null, null, Duration.ofMinutes(5));
		HandleInboundRequest make = new HandleInboundRequest(null, null, true,
				new com.muhjain.school.model.dto.request.EnquiryFromReplyRequest("A", null, "1", "Tohana"));
		assertThatThrownBy(() -> inboundMessages.handle(indian, make, Set.of(Permission.MARKETING_SEND), owner.getId()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(403));
		long abroad = reply("+447700900123", "Admission?", null, null, Duration.ofMinutes(4));
		assertThatThrownBy(() -> inboundMessages.handle(abroad, make, Role.OWNER.permissions(), owner.getId()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("VALIDATION"));
		assertThat(jdbc.queryForObject("select count(*) from enquiry", Integer.class)).isEqualTo(2);   // none made
	}

	@Test
	void theListPageSizeIsChecked() throws Exception {
		call(get("/api/v1/inbound-messages?size=0"), null).andExpect(status().isBadRequest());
		call(get("/api/v1/message-stops?size=500"), null).andExpect(status().isBadRequest());
	}

}
