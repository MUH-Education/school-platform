package com.muhjain.school.controller;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.InboundWebhookRequest;
import com.muhjain.school.model.dto.request.StatusWebhookRequest;
import com.muhjain.school.model.dto.request.TemplateStatusWebhookRequest;
import com.muhjain.school.model.dto.response.WebhookResponse;
import com.muhjain.school.service.messaging.DeliveryStatusService;
import com.muhjain.school.service.messaging.WebhookSignature;
import com.muhjain.school.service.notification.InboundMessageService;
import com.muhjain.school.service.notification.NotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Pattern;

/**
 * The provider's callbacks (docs/10-notifications.md section 8). The only open URLs of the module: no token, but
 * every call must be signed with the shared secret (HMAC-SHA256 of the raw body), else 401 BAD_SIGNATURE and nothing
 * is read or saved (.claude/rules/security.md). The body is checked only after the signature.
 * A repeat or an unknown message is still 200, so the provider stops sending it.
 */
@RestController
@RequestMapping("/api/v1/webhooks/{provider}")
@Tag(name = "Notifications")
public class WebhookController {

	private static final Pattern PROVIDER = Pattern.compile("[a-z0-9-]{1,30}");

	private final WebhookSignature signature;

	private final JsonMapper jsonMapper;

	private final DeliveryStatusService deliveryStatus;

	private final InboundMessageService inboundMessages;

	private final NotificationTemplateService templates;

	public WebhookController(WebhookSignature signature, JsonMapper jsonMapper, DeliveryStatusService deliveryStatus,
			InboundMessageService inboundMessages, NotificationTemplateService templates) {
		this.signature = signature;
		this.jsonMapper = jsonMapper;
		this.deliveryStatus = deliveryStatus;
		this.inboundMessages = inboundMessages;
		this.templates = templates;
	}

	/** Delivered, read or failed. Example body: {"providerRef":"wamid.1","status":"READ","at":"..."} */
	@Operation(summary = "Provider callback: delivered / read / failed (signed, no token)")
	@SecurityRequirements
	@PostMapping(value = "/status", consumes = MediaType.APPLICATION_JSON_VALUE)
	public WebhookResponse status(@PathVariable String provider, @RequestBody byte[] body, HttpServletRequest http) {
		StatusWebhookRequest request = read(provider, body, http, StatusWebhookRequest.class);
		return new WebhookResponse(true, deliveryStatus.apply(request));
	}

	/** A message that came in: a text, or a tap on a button. STOP puts the family on the stop list. */
	@Operation(summary = "Provider callback: a reply or a button tap (signed, no token)")
	@SecurityRequirements
	@PostMapping(value = "/inbound", consumes = MediaType.APPLICATION_JSON_VALUE)
	public WebhookResponse inbound(@PathVariable String provider, @RequestBody byte[] body, HttpServletRequest http) {
		InboundWebhookRequest request = read(provider, body, http, InboundWebhookRequest.class);
		return new WebhookResponse(true, inboundMessages.receive(request));
	}

	/** WhatsApp approved or rejected a template. */
	@Operation(summary = "Provider callback: template approved / rejected (signed, no token)")
	@SecurityRequirements
	@PostMapping(value = "/template-status", consumes = MediaType.APPLICATION_JSON_VALUE)
	public WebhookResponse templateStatus(@PathVariable String provider, @RequestBody byte[] body,
			HttpServletRequest http) {
		TemplateStatusWebhookRequest request = read(provider, body, http, TemplateStatusWebhookRequest.class);
		if (request.templateName() == null || request.templateName().isBlank() || request.status() == null) {
			throw ApiException.validation("templateName", "templateName and status are needed");
		}
		return new WebhookResponse(true,
				templates.applyProviderDecision(request.templateName().strip(), request.status(), request.reason()));
	}

	// The signature first, the JSON after: an unsigned body is never parsed.
	private <T> T read(String provider, byte[] body, HttpServletRequest http, Class<T> type) {
		if (!PROVIDER.matcher(provider).matches()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown provider.");
		}
		if (!signature.valid(body, http.getHeader(signature.headerName()))) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_SIGNATURE",
					"The signature of this callback is missing or wrong.");
		}
		try {
			return jsonMapper.readValue(body, type);
		}
		catch (JacksonException ex) {
			throw ApiException.validation("body", "is not valid JSON for this callback");
		}
	}

}
