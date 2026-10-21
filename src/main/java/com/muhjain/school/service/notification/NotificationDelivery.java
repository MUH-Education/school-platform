package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.SendContext;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.TemplateApproval;
import com.muhjain.school.model.repository.MessageTemplateRepository;
import com.muhjain.school.model.repository.NotificationRepository;
import com.muhjain.school.util.Placeholders;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What {@code OutboxWorker} needs to send a notification row (docs/10-notifications.md section 6): the template's
 * name at the provider, its language, the values in the order of the template's {boxes}, the provider's media id.
 * And the SMS copy when a phone has no WhatsApp. The row itself stays complete (its values are on it), so a row can
 * be sent again on its own.
 */
@Component
public class NotificationDelivery {

	private final NotificationRepository notifications;

	private final MessageTemplateRepository templates;

	private final KindPolicies policies;

	private final Clock clock;

	public NotificationDelivery(NotificationRepository notifications, MessageTemplateRepository templates,
			KindPolicies policies, Clock clock) {
		this.notifications = notifications;
		this.templates = templates;
		this.policies = policies;
		this.clock = clock;
	}

	/**
	 * The send context of a notification row. Example: template "open_day_oct", hi, params ["Rajesh Punia",
	 * "Aryan और Siya"] for a body "नमस्ते {Parent name}, {Child name} ...". A template that is not approved means
	 * free text (only ONE_FAMILY and STAFF get this far with one: the guards checked the 24-hour window).
	 */
	public SendContext contextFor(MessageOutbox row) {
		Notification n = notifications.findById(row.getNotificationId()).orElseThrow();
		MessageTemplate t = templates.findById(n.getTemplateId()).orElseThrow();
		Map<String, String> values = row.getTemplateValues();
		List<String> params = Placeholders.of(t.textToSend())
			.stream()
			.map(box -> values.getOrDefault(box, ""))
			.toList();
		boolean freeText = t.getApproval() != TemplateApproval.APPROVED;
		String mediaId = (row.getChannel() == MessageChannel.WHATSAPP) ? n.getProviderMediaId() : null;
		return new SendContext(t.getProviderTemplateId(), t.getProviderTemplateName(), t.getProviderLanguage(), params,
				freeText, mediaId);
	}

	/**
	 * The SMS copy of a WhatsApp row whose phone has no WhatsApp: same person, the notification's SMS text filled
	 * with the row's values, {@code fallback_of_id} = the WhatsApp row. Empty when there is no SMS text, or when the
	 * kind already sends an SMS to everyone (URGENT: the person has an SMS row already).
	 * Example: the Malik family is not on WhatsApp → "PTM शनिवार को। – MUH Jain School" by SMS.
	 */
	public Optional<MessageOutbox> smsFallback(MessageOutbox whatsApp) {
		if (whatsApp.getNotificationId() == null || whatsApp.getChannel() != MessageChannel.WHATSAPP) {
			return Optional.empty();
		}
		Notification n = notifications.findById(whatsApp.getNotificationId()).orElseThrow();
		if (n.getSmsBody() == null || policies.forKind(n.getKind()).alsoSmsWhenOnWhatsApp()) {
			return Optional.empty();
		}
		String text = Placeholders.fill(n.getSmsBody(), whatsApp.getTemplateValues());
		MessageOutbox sms = new MessageOutbox(whatsApp.getPurpose(), MessageChannel.SMS, whatsApp.getPhone(),
				(text.length() <= 1024) ? text : text.substring(0, 1024), Instant.now(clock));
		sms.setNotificationId(n.getId());
		sms.setFallbackOfId(whatsApp.getId());
		sms.setGuardianId(whatsApp.getGuardianId());
		sms.setStaffId(whatsApp.getStaffId());
		sms.setEnquiryId(whatsApp.getEnquiryId());
		sms.setTemplateValues(whatsApp.getTemplateValues());
		sms.setCreatedBy(whatsApp.getCreatedBy());
		return Optional.of(sms);
	}

}
