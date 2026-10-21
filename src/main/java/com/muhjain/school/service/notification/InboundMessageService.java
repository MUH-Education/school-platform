package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.InboundWebhookRequest;
import com.muhjain.school.model.entity.InboundMessage;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.enums.ButtonAction;
import com.muhjain.school.model.enums.StopReason;
import com.muhjain.school.model.repository.InboundMessageRepository;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.service.enquiry.EnquiryService;
import com.muhjain.school.service.staff.StaffService;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Every WhatsApp message that comes in to the school number (docs/10-notifications.md section 5, rule 17).
 * <ul>
 * <li>Saved once: the provider's id is unique, the same callback twice saves one row.</li>
 * <li>Linked to the parent, employee and enquiry with that phone, and to our message it answers.</li>
 * <li>STOP — the text "stop" (any case) or "बंद", or the "Stop messages" button — puts the family on the stop list
 * and the reply is handled by the server. Example: the Malik family writes "Stop" at 6:29 pm; the 6:30 pm open-day
 * message skips them; a notice still reaches them.</li>
 * <li>A tapped button is saved on our message too, for the report.</li>
 * <li>Everything else waits for a person (the provider's inbox for now; the list comes in 11.15).</li>
 * </ul>
 * The text and the full phone are never logged.
 */
@Service
public class InboundMessageService {

	private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);

	private static final Set<String> STOP_WORDS = Set.of("stop", "बंद");

	private static final Pattern ANY_PHONE = Pattern.compile("\\+[1-9][0-9]{6,14}");

	private final InboundMessageRepository inbound;

	private final MessageOutboxRepository outbox;

	private final StopListService stopList;

	private final GuardianService guardianService;

	private final StaffService staffService;

	private final EnquiryService enquiryService;

	private final Clock clock;

	public InboundMessageService(InboundMessageRepository inbound, MessageOutboxRepository outbox,
			StopListService stopList, GuardianService guardianService, StaffService staffService,
			EnquiryService enquiryService, Clock clock) {
		this.inbound = inbound;
		this.outbox = outbox;
		this.stopList = stopList;
		this.guardianService = guardianService;
		this.staffService = staffService;
		this.enquiryService = enquiryService;
		this.clock = clock;
	}

	/**
	 * @return true when the message was new, false when the same callback came before
	 * @throws ApiException 400 VALIDATION (no providerRef, no phone, neither text nor button)
	 */
	@Transactional
	public boolean receive(InboundWebhookRequest request) {
		if (request.providerRef() == null || request.providerRef().isBlank()) {
			throw ApiException.validation("providerRef", "is needed");
		}
		String text = (request.text() == null || request.text().isBlank()) ? null : request.text().strip();
		if (text == null && request.buttonAction() == null) {
			throw ApiException.validation("text", "a text or a buttonAction is needed");
		}
		String ref = request.providerRef().strip();
		if (inbound.findByProviderRef(ref).isPresent()) {
			return false;
		}
		String phone = phoneOf(request.phone());

		InboundMessage message = new InboundMessage();
		message.setProviderRef(ref);
		message.setPhone(phone);
		message.setText((text == null || text.length() <= 4096) ? text : text.substring(0, 4096));
		message.setButtonAction(request.buttonAction());
		message.setReceivedAt(((request.receivedAt() == null) ? Instant.now(clock) : request.receivedAt().toInstant())
			.truncatedTo(ChronoUnit.MICROS));
		guardianService.idByPhone(phone).ifPresent(message::setGuardianId);
		staffService.activeIdByPhone(phone).ifPresent(message::setStaffId);
		enquiryService.latestIdByPhone(phone).ifPresent(message::setEnquiryId);

		Optional<MessageOutbox> answered = (request.inReplyTo() == null || request.inReplyTo().isBlank())
				? Optional.empty() : outbox.findFirstByProviderRef(request.inReplyTo().strip());
		answered.ifPresent(row -> {
			message.setInReplyToOutboxId(row.getId());
			if (request.buttonAction() != null) {
				row.setButtonAction(request.buttonAction());
				outbox.save(row);
			}
		});

		decide(message, text, answered.map(MessageOutbox::getNotificationId).orElse(null));
		inbound.save(message);
		log.info("Reply {} from {} saved, handled: {}", message.getId(), PhoneNumbers.mask(phone),
				message.isHandled());
		return true;
	}

	// What the server can do by itself; the rest waits for a person, with a note.
	private void decide(InboundMessage message, String text, Long notificationId) {
		ButtonAction button = message.getButtonAction();
		boolean stop = button == ButtonAction.STOP_MESSAGES
				|| (text != null && STOP_WORDS.contains(text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}।\\s]+$", "")));
		if (stop) {
			if (message.getGuardianId() == null) {
				message.setNote("Wrote STOP, but this phone is not a parent here: nothing changed.");
				return;
			}
			StopReason reason = (button == ButtonAction.STOP_MESSAGES) ? StopReason.TAPPED_BUTTON : StopReason.WROTE_STOP;
			boolean added = stopList.add(message.getGuardianId(), reason, notificationId);
			message.setHandled(true);
			message.setNote(added ? "Put on the stop list." : "Was on the stop list already.");
			return;
		}
		if (button == ButtonAction.GOT_IT) {
			message.setHandled(true);
			message.setNote("Tapped \"Got it\".");
		}
		else if (button == ButtonAction.BOOK_VISIT) {
			// An enquiry needs a village, a class and a source that a button tap does not give: a person calls back.
			message.setNote("Wants to book a school visit. Call back and add an enquiry.");
		}
		else if (button == ButtonAction.CALL_ME) {
			message.setNote("Asked for a call.");
		}
	}

	// An Indian mobile as +91XXXXXXXXXX (also from "919811100001"); a number from abroad as "+" and its digits.
	static String phoneOf(String raw) {
		if (raw == null || raw.isBlank()) {
			throw ApiException.validation("phone", "is needed");
		}
		try {
			return PhoneNumbers.normalize(raw);
		}
		catch (ApiException notIndian) {
			String other = "+" + raw.replaceAll("[^0-9]", "");
			if (!ANY_PHONE.matcher(other).matches()) {
				throw ApiException.validation("phone", "is not a phone number");
			}
			return other;
		}
	}

}
