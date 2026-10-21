package com.muhjain.school.notification;

import com.jayway.jsonpath.JsonPath;
import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.config.NotificationProperties;
import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.TemplateApprovalRequest;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.model.enums.TemplateApproval;
import com.muhjain.school.service.notification.NotificationTemplateService;
import com.muhjain.school.service.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/v1/notification-templates}: docs/10-notifications.md section 4, task 11.7. */
class NotificationTemplateTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/notification-templates";

	@Autowired
	private FileStorage storage;

	private final List<String> files = new ArrayList<>();

	private String owner;

	@BeforeEach
	void owner() {
		owner = tokenFor(addUser("+919812340001", Role.OWNER));
	}

	@AfterEach
	void removeFiles() throws IOException {
		for (String key : files) {
			storage.delete(key);
		}
	}

	private ResultActions send(String method, String url, String json) throws Exception {
		var builder = switch (method) {
			case "POST" -> post(url);
			case "PUT" -> put(url);
			default -> get(url);
		};
		if (json != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(builder.header("Authorization", bearer(owner)));
	}

	private static String template(String kind, String body, String buttons, String examples) {
		return "{\"name\":\"Open day\",\"kind\":\"" + kind + "\",\"body\":\"" + body + "\",\"buttons\":" + buttons
				+ ",\"exampleValues\":" + examples + "}";
	}

	private static final String OPEN_DAY = template("MARKETING", "नमस्ते {Parent name}, रविवार को open day है।",
			"[{\"type\":\"CALL\",\"text\":\"Call school\",\"action\":\"98000 00100\"}]",
			"{\"Parent name\":\"Rajesh Punia\",\"Unused\":\"x\"}");

	private long create(String json) throws Exception {
		String answer = send("POST", URL, json).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(answer, "$.id")).longValue();
	}

	private void approve(long id) throws Exception {
		send("PUT", URL + "/" + id + "/approval", "{\"approval\":\"APPROVED\"}").andExpect(status().isOk());
	}

	@Test
	void marketingTemplateGetsTheStopButtonLast() throws Exception {
		send("POST", URL, OPEN_DAY).andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("MARKETING"))
			.andExpect(jsonPath("$.approval").value("WAITING"))
			.andExpect(jsonPath("$.language").value("hi"))
			.andExpect(jsonPath("$.headerType").value("NONE"))
			.andExpect(jsonPath("$.buttons", hasSize(2)))
			.andExpect(jsonPath("$.buttons[0].action").value("+919800000100"))   // the phone is tidied
			.andExpect(jsonPath("$.buttons[0].fixed").value(false))
			.andExpect(jsonPath("$.buttons[1].type").value("REPLY"))
			.andExpect(jsonPath("$.buttons[1].action").value("STOP_MESSAGES"))
			.andExpect(jsonPath("$.buttons[1].text").value("मैसेज बंद करें"))
			.andExpect(jsonPath("$.buttons[1].fixed").value(true))
			// Only the examples of boxes that are really in the text are kept.
			.andExpect(jsonPath("$.exampleValues.length()").value(1));
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'NOTIFICATION_TEMPLATE'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void stopButtonRules() throws Exception {
		// Three of your own + "Stop messages" = 4: too many for a marketing template.
		String three = "[{\"type\":\"REPLY\",\"text\":\"Book a visit\",\"action\":\"BOOK_VISIT\"},"
				+ "{\"type\":\"REPLY\",\"text\":\"Call me\",\"action\":\"CALL_ME\"},"
				+ "{\"type\":\"URL\",\"text\":\"Photos\",\"action\":\"https://muhjain.school\"}]";
		send("POST", URL, template("GREETING", "Happy Diwali", three, "{}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.buttons").value("at most 2 buttons: the third is \"Stop messages\", "
					+ "added by the school"));
		// The same three are fine on a notice, which has no stop button.
		send("POST", URL, template("NOTICE", "PTM on Saturday", three, "{}")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.buttons", hasSize(3)));
		// A notice cannot offer "Stop messages": the family would still get notices.
		send("POST", URL, template("NOTICE", "PTM", "[{\"type\":\"REPLY\",\"text\":\"Stop\",\"action\":\"STOP_MESSAGES\"}]",
				"{}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields['buttons[0].action']").exists());
		// Sending it yourself on a marketing template does not make two.
		send("POST", URL, template("MARKETING", "Admissions open",
				"[{\"type\":\"REPLY\",\"text\":\"Stop\",\"action\":\"STOP_MESSAGES\"}]", "{}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.buttons", hasSize(1)));
	}

	@Test
	void buttonsAreChecked() throws Exception {
		send("POST", URL, template("NOTICE", "x", "[{\"type\":\"URL\",\"text\":\"Site\",\"action\":\"http://a.b\"}]",
				"{}")).andExpect(status().isBadRequest());
		send("POST", URL, template("NOTICE", "x", "[{\"type\":\"CALL\",\"text\":\"Call\",\"action\":\"12345\"}]",
				"{}")).andExpect(status().isBadRequest());
		send("POST", URL, template("NOTICE", "x", "[{\"type\":\"REPLY\",\"text\":\"Buy\",\"action\":\"BUY_NOW\"}]",
				"{}")).andExpect(status().isBadRequest());
		send("POST", URL, template("NOTICE", "x",
				"[{\"type\":\"REPLY\",\"text\":\"This text is much too long\",\"action\":\"GOT_IT\"}]", "{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"));
	}

	@Test
	void everyBoxNeedsAnExample() throws Exception {
		send("POST", URL, template("NOTICE", "{Child name} की PTM शनिवार को", "[]", "{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.exampleValues").value("needs an example for {Child name}"));
	}

	@Test
	void aHeaderNeedsItsFile() throws Exception {
		String poster = storage.save("notifications", MediaFiles.png(10, 10), "png");
		files.add(poster);
		String withHeader = "{\"name\":\"Diwali\",\"kind\":\"GREETING\",\"body\":\"Happy Diwali\","
				+ "\"headerType\":\"%s\",\"mediaKey\":\"%s\"}";
		send("POST", URL, withHeader.formatted("IMAGE", poster)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.mediaKey").value(poster));
		// A picture is not a video, a missing file is missing, a header needs a file.
		send("POST", URL, withHeader.formatted("VIDEO", poster)).andExpect(status().isBadRequest());
		send("POST", URL, withHeader.formatted("IMAGE", "notifications/nothing.png")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.mediaKey").value("does not exist"));
		send("POST", URL, "{\"name\":\"D\",\"kind\":\"GREETING\",\"body\":\"x\",\"headerType\":\"IMAGE\"}")
			.andExpect(status().isBadRequest());
		// Only our own notification files: never a student photo.
		send("POST", URL, withHeader.formatted("IMAGE", "students/a.png")).andExpect(status().isBadRequest());
	}

	@Test
	void editingAnApprovedTemplateMakesANewVersion() throws Exception {
		long first = create(OPEN_DAY);
		approve(first);
		String changed = OPEN_DAY.replace("रविवार", "शनिवार");
		String answer = send("PUT", URL + "/" + first, changed).andExpect(status().isOk())
			.andExpect(jsonPath("$.approval").value("WAITING"))
			.andExpect(jsonPath("$.replacesId").value(first))
			.andReturn().getResponse().getContentAsString();
		long second = ((Number) JsonPath.read(answer, "$.id")).longValue();
		assertThat(second).isNotEqualTo(first);
		// The old version stays as it was (old notifications point at it), turned off, out of the list.
		assertThat(jdbc.queryForMap("select active, approval, body from message_template where id = ?", first))
			.containsEntry("active", false)
			.containsEntry("approval", "APPROVED");
		send("GET", URL + "?kind=MARKETING", null).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(second));
		send("PUT", URL + "/" + first, changed).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("TEMPLATE_REPLACED"));
	}

	@Test
	void aWaitingOrRejectedTemplateIsChangedInPlace() throws Exception {
		long id = create(OPEN_DAY);
		send("PUT", URL + "/" + id + "/approval", "{\"approval\":\"REJECTED\",\"reason\":\"Too promotional\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rejectionReason").value("Too promotional"));
		send("PUT", URL + "/" + id, OPEN_DAY.replace("open day", "school visit")).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.approval").value("WAITING"))
			.andExpect(jsonPath("$.rejectionReason").doesNotExist())
			.andExpect(jsonPath("$.buttons", hasSize(2)));
		assertThat(jdbc.queryForObject("select count(*) from message_template_button where template_id = ?",
				Integer.class, id)).isEqualTo(2);
	}

	@Test
	void theKindNeverChanges() throws Exception {
		long id = create(OPEN_DAY);
		send("PUT", URL + "/" + id, OPEN_DAY.replace("MARKETING", "NOTICE")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("TEMPLATE_KIND_FIXED"));
	}

	@Test
	void approvalByHand() throws Exception {
		long id = create(OPEN_DAY);
		send("PUT", URL + "/" + id + "/approval", "{\"approval\":\"REJECTED\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.reason").value("is needed for REJECTED"));
		send("PUT", URL + "/" + id + "/approval", "{\"approval\":\"WAITING\"}").andExpect(status().isBadRequest());
		approve(id);
		// What was approved is fixed: the text that will be sent.
		assertThat(jdbc.queryForObject("select approved_body from message_template where id = ?", String.class, id))
			.isEqualTo("नमस्ते {Parent name}, रविवार को open day है।");
	}

	@Test
	void approvalByHandIsOffWhereTheProviderDecides() {
		NotificationTemplateService prod = new NotificationTemplateService(null, null, null, null,
				new NotificationProperties(false), null, null);
		assertThatThrownBy(() -> prod.decide(1L, new TemplateApprovalRequest(TemplateApproval.APPROVED, null), 1L))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(404));
	}

	@Test
	void systemTextsAreNotNotificationTemplates() throws Exception {
		long bus = jdbc.queryForObject("select id from message_template where code = 'BOARDED_MORNING' and gender = 'M'",
				Long.class);
		send("GET", URL + "/" + bus, null).andExpect(status().isNotFound());
		send("PUT", URL + "/" + bus, OPEN_DAY).andExpect(status().isNotFound());
		send("GET", URL, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

}
