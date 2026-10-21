package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.EnquiryFromReplyRequest;
import com.muhjain.school.model.dto.request.EnquiryRequest;
import com.muhjain.school.model.dto.request.HandleInboundRequest;
import com.muhjain.school.model.dto.request.InboundWebhookRequest;
import com.muhjain.school.model.dto.response.FamilyContact;
import com.muhjain.school.model.dto.response.InboundMessageResponse;
import com.muhjain.school.model.dto.response.PageResponse;
import com.muhjain.school.model.entity.InboundMessage;
import com.muhjain.school.model.entity.MessageOutbox;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.ButtonAction;
import com.muhjain.school.model.enums.EnquirySource;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.model.enums.StopReason;
import com.muhjain.school.model.repository.InboundMessageRepository;
import com.muhjain.school.model.repository.MessageOutboxRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.enquiry.EnquiryService;
import com.muhjain.school.service.staff.StaffService;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.service.user.UserService;
import com.muhjain.school.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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

	static final String ENTITY = "INBOUND_MESSAGE";

	private static final Set<String> STOP_WORDS = Set.of("stop", "बंद");

	private static final Pattern ANY_PHONE = Pattern.compile("\\+[1-9][0-9]{6,14}");

	private final InboundMessageRepository inbound;

	private final MessageOutboxRepository outbox;

	private final StopListService stopList;

	private final GuardianService guardianService;

	private final StaffService staffService;

	private final EnquiryService enquiryService;

	private final UserService userService;

	private final AuditService auditService;

	private final Clock clock;

	public InboundMessageService(InboundMessageRepository inbound, MessageOutboxRepository outbox,
			StopListService stopList, GuardianService guardianService, StaffService staffService,
			EnquiryService enquiryService, UserService userService, AuditService auditService, Clock clock) {
		this.userService = userService;
		this.auditService = auditService;
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

	/** The replies, newest first. {@code handled} null = all; false = the ones waiting for a person. */
	@Transactional(readOnly = true)
	public PageResponse<InboundMessageResponse> list(Boolean handled, int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw ApiException.validation("size", "page must be 0 or more, size 1 to 100");
		}
		PageRequest request = PageRequest.of(page, size);
		Page<InboundMessage> found = (handled == null) ? inbound.findAllByOrderByReceivedAtDescIdDesc(request)
				: inbound.findByHandledOrderByReceivedAtDescIdDesc(handled, request);
		List<InboundMessage> items = found.getContent();
		Map<Long, FamilyContact> parents = guardianService.contacts(ids(items, InboundMessage::getGuardianId));
		Map<Long, String> staff = staffService.names(ids(items, InboundMessage::getStaffId));
		Map<Long, String> enquiries = enquiryService.parentNames(ids(items, InboundMessage::getEnquiryId));
		Map<Long, String> users = userService.displayNames(ids(items, InboundMessage::getHandledBy));
		return PageResponse.of(found.map(m -> toResponse(m, parents, staff, enquiries, users)));
	}

	/**
	 * Marks a reply as done, with what the office did (rule 20: "promised until" stops fee reminders until that
	 * day). With {@code makeEnquiry}, an enquiry is made with the reply's phone, source WHATSAPP. Example: Mrs
	 * Sharma wrote "will pay by Friday" → handled, promised until Friday, no fee reminder to her before Saturday.
	 *
	 * @param permissions the user's permissions: making an enquiry also needs ENQUIRIES_EDIT
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION, 403 FORBIDDEN (enquiry without ENQUIRIES_EDIT),
	 * 409 ENQUIRY_EXISTS (from the enquiry rules)
	 */
	@Transactional
	public InboundMessageResponse handle(Long id, HandleInboundRequest request, Set<Permission> permissions,
			Long userId) {
		InboundMessage message = inbound.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This reply does not exist."));
		LocalDate today = LocalDate.now(clock);
		if (request.promisedUntil() != null
				&& (request.promisedUntil().isBefore(today) || request.promisedUntil().isAfter(today.plusDays(90)))) {
			throw ApiException.validation("promisedUntil", "must be from today to 90 days ahead");
		}
		if (Boolean.TRUE.equals(request.makeEnquiry())) {
			message.setEnquiryId(makeEnquiry(message, request, permissions, userId));
		}
		message.setHandled(true);
		message.setHandledBy(userId);
		if (request.note() != null && !request.note().isBlank()) {
			message.setNote(request.note().strip());
		}
		if (request.promisedUntil() != null) {
			message.setPromisedUntil(request.promisedUntil());
		}
		inbound.saveAndFlush(message);
		String summary = "Reply from " + PhoneNumbers.mask(message.getPhone()) + " handled"
				+ ((request.promisedUntil() == null) ? "." : ": promised to pay by " + request.promisedUntil() + ".");
		auditService.record(ENTITY, id, AuditAction.UPDATED, summary,
				(request.promisedUntil() == null) ? Map.of() : Map.of("promisedUntil", request.promisedUntil().toString()));
		return responseOf(message);
	}

	private Long makeEnquiry(InboundMessage message, HandleInboundRequest request, Set<Permission> permissions,
			Long userId) {
		if (!permissions.contains(Permission.ENQUIRIES_EDIT)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You may not add enquiries.");
		}
		if (request.enquiry() == null) {
			throw ApiException.validation("enquiry", "parent name, class and village are needed to make an enquiry");
		}
		if (!message.getPhone().startsWith("+91")) {
			throw ApiException.validation("makeEnquiry", "an enquiry needs an Indian mobile number");
		}
		EnquiryFromReplyRequest e = request.enquiry();
		EnquiryRequest enquiry = new EnquiryRequest(e.parentName(), message.getPhone(), null, e.village(),
				e.childName(), e.classSought(), null, null, EnquirySource.WHATSAPP, null, null, null, null, null, null,
				"From a WhatsApp reply.", null);
		return enquiryService.create(enquiry, userId).id();
	}

	// The one handled reply as the list shows it.
	private InboundMessageResponse responseOf(InboundMessage m) {
		List<InboundMessage> one = List.of(m);
		return toResponse(m, guardianService.contacts(ids(one, InboundMessage::getGuardianId)),
				staffService.names(ids(one, InboundMessage::getStaffId)),
				enquiryService.parentNames(ids(one, InboundMessage::getEnquiryId)),
				userService.displayNames(ids(one, InboundMessage::getHandledBy)));
	}

	private InboundMessageResponse toResponse(InboundMessage m, Map<Long, FamilyContact> parents,
			Map<Long, String> staff, Map<Long, String> enquiries, Map<Long, String> users) {
		RecipientType type = null;
		Long fromId = null;
		String name = null;
		if (m.getGuardianId() != null) {
			type = RecipientType.GUARDIAN;
			fromId = m.getGuardianId();
			FamilyContact c = parents.get(fromId);
			name = (c == null) ? null : c.name();
		}
		else if (m.getStaffId() != null) {
			type = RecipientType.STAFF;
			fromId = m.getStaffId();
			name = staff.get(fromId);
		}
		else if (m.getEnquiryId() != null) {
			type = RecipientType.ENQUIRY;
			fromId = m.getEnquiryId();
			name = enquiries.get(fromId);
		}
		return new InboundMessageResponse(m.getId(), m.getPhone(), type, fromId, name, m.getText(), m.getButtonAction(),
				m.getInReplyToOutboxId(), m.getReceivedAt().atZone(clock.getZone()).toOffsetDateTime(), m.isHandled(),
				m.getHandledBy(), (m.getHandledBy() == null) ? null : users.get(m.getHandledBy()), m.getNote(),
				m.getPromisedUntil(), m.getEnquiryId());
	}

	private static List<Long> ids(List<InboundMessage> items, Function<InboundMessage, Long> id) {
		return items.stream().map(id).filter(Objects::nonNull).distinct().toList();
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
