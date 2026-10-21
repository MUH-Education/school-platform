package com.muhjain.school.service.notification;

import com.muhjain.school.config.NotificationProperties;
import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.NotificationTemplateRequest;
import com.muhjain.school.model.dto.request.TemplateApprovalRequest;
import com.muhjain.school.model.dto.request.TemplateButtonRequest;
import com.muhjain.school.model.dto.response.NotificationTemplateResponse;
import com.muhjain.school.model.dto.response.TemplateButtonResponse;
import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.entity.MessageTemplateButton;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.ButtonAction;
import com.muhjain.school.model.enums.ButtonType;
import com.muhjain.school.model.enums.HeaderType;
import com.muhjain.school.model.enums.MediaFileType;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.TemplateApproval;
import com.muhjain.school.model.enums.TemplateKind;
import com.muhjain.school.model.repository.MessageTemplateButtonRepository;
import com.muhjain.school.model.repository.MessageTemplateRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.util.PhoneNumbers;
import com.muhjain.school.util.Placeholders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The "saved texts" of the Notification module (docs/10-notifications.md section 4). They live in
 * {@code message_template} next to the SYSTEM texts of Phase 5, which this service never shows or touches.
 * <ul>
 * <li>The kind is set once and never changes.</li>
 * <li>A MARKETING or GREETING template always ends with a REPLY button "Stop messages", added here.</li>
 * <li>Editing an APPROVED template makes a new row ({@code replacesId} = the old one, which is turned off) that
 * waits for approval again. Old notifications keep pointing at the old row. A WAITING or REJECTED template is
 * changed in place and waits again.</li>
 * <li>Approval is set by hand only where {@code app.notifications.manual-approval} is on (local, test).</li>
 * </ul>
 */
@Service
public class NotificationTemplateService {

	static final String ENTITY = "NOTIFICATION_TEMPLATE";

	private final MessageTemplateRepository templates;

	private final MessageTemplateButtonRepository buttons;

	private final KindPolicies policies;

	private final NotificationMediaService mediaService;

	private final NotificationProperties properties;

	private final AuditService auditService;

	private final Clock clock;

	public NotificationTemplateService(MessageTemplateRepository templates, MessageTemplateButtonRepository buttons,
			KindPolicies policies, NotificationMediaService mediaService, NotificationProperties properties,
			AuditService auditService, Clock clock) {
		this.templates = templates;
		this.buttons = buttons;
		this.policies = policies;
		this.mediaService = mediaService;
		this.properties = properties;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** The templates in use, by name. {@code kind} null = every kind. The SYSTEM texts are never in this list. */
	@Transactional(readOnly = true)
	public List<NotificationTemplateResponse> list(NotificationKind kind) {
		List<MessageTemplate> found = (kind == null)
				? templates.findByKindNotAndActiveTrueOrderByNameAsc(TemplateKind.SYSTEM)
				: templates.findByKindAndActiveTrueOrderByNameAsc(TemplateKind.of(kind));
		Map<Long, List<MessageTemplateButton>> byTemplate = buttons
			.findByTemplateIdInOrderByTemplateIdAscSeqNoAsc(found.stream().map(MessageTemplate::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(MessageTemplateButton::getTemplateId));
		return found.stream().map(t -> toResponse(t, byTemplate.getOrDefault(t.getId(), List.of()))).toList();
	}

	/** One notification template. @throws ApiException 404 NOT_FOUND (also for a SYSTEM text) */
	@Transactional(readOnly = true)
	public NotificationTemplateResponse get(Long id) {
		MessageTemplate template = find(id);
		return toResponse(template, buttons.findByTemplateIdOrderBySeqNoAsc(id));
	}

	/**
	 * A new template, WAITING for approval.
	 *
	 * @throws ApiException 400 VALIDATION (media, buttons, a {box} with no example)
	 */
	@Transactional
	public NotificationTemplateResponse create(NotificationTemplateRequest request, Long userId) {
		Checked checked = check(request);
		MessageTemplate template = new MessageTemplate(TemplateKind.of(request.kind()), request.name().strip(),
				request.body().strip(), userId);
		fill(template, request, checked);
		templates.save(template);
		List<MessageTemplateButton> saved = saveButtons(template.getId(), checked.buttons());
		auditService.record(ENTITY, template.getId(), AuditAction.CREATED,
				"Template \"" + template.getName() + "\" made (" + request.kind() + "), waiting for approval.",
				Map.of("kind", request.kind().name(), "buttons", saved.size()));
		return toResponse(template, saved);
	}

	/**
	 * Changes a template. An APPROVED one is not changed: a new row is made that waits for approval, and the old one
	 * is turned off. Example: "Open day" (approved, id 31) gets a new date → id 32, WAITING, replaces 31.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 TEMPLATE_REPLACED (a newer version exists), 409 TEMPLATE_KIND_FIXED,
	 * 400 VALIDATION
	 */
	@Transactional
	public NotificationTemplateResponse update(Long id, NotificationTemplateRequest request, Long userId) {
		MessageTemplate current = find(id);
		requireActive(current);
		if (current.getKind() != TemplateKind.of(request.kind())) {
			throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_KIND_FIXED",
					"The kind of a template cannot change. Make a new template instead.");
		}
		Checked checked = check(request);
		if (current.getApproval() == TemplateApproval.APPROVED) {
			current.setActive(false);
			MessageTemplate next = new MessageTemplate(current.getKind(), request.name().strip(),
					request.body().strip(), userId);
			fill(next, request, checked);
			next.setReplacesId(current.getId());
			templates.save(next);
			List<MessageTemplateButton> saved = saveButtons(next.getId(), checked.buttons());
			auditService.record(ENTITY, next.getId(), AuditAction.CREATED, "Template \"" + next.getName()
					+ "\" changed: new version " + next.getId() + " of " + current.getId() + ", waiting for approval.",
					Map.of("replaces", current.getId()));
			return toResponse(next, saved);
		}
		current.setName(request.name().strip());
		current.setBody(request.body().strip());
		fill(current, request, checked);
		current.setApproval(TemplateApproval.WAITING, null, userId);
		current.setApprovedBody(null);
		templates.saveAndFlush(current);
		buttons.deleteByTemplateId(id);
		buttons.flush();
		List<MessageTemplateButton> saved = saveButtons(id, checked.buttons());
		auditService.record(ENTITY, id, AuditAction.UPDATED,
				"Template \"" + current.getName() + "\" changed, waiting for approval.", Map.of());
		return toResponse(current, saved);
	}

	/**
	 * The provider's answer, typed by the owner. Only where manual approval is on (local, test); elsewhere the
	 * provider's webhook decides and this answers 404. APPROVED fixes the text that was approved.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 TEMPLATE_REPLACED, 400 VALIDATION (WAITING, or REJECTED with no reason)
	 */
	@Transactional
	public NotificationTemplateResponse decide(Long id, TemplateApprovalRequest request, Long userId) {
		if (!properties.manualApproval()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Approval comes from the WhatsApp provider.");
		}
		MessageTemplate template = find(id);
		requireActive(template);
		if (request.approval() == TemplateApproval.WAITING) {
			throw ApiException.validation("approval", "must be APPROVED or REJECTED");
		}
		String reason = (request.reason() == null) ? null : request.reason().strip();
		if (request.approval() == TemplateApproval.REJECTED && (reason == null || reason.isEmpty())) {
			throw ApiException.validation("reason", "is needed for REJECTED");
		}
		template.setApproval(request.approval(), reason, userId);
		template.setApprovedBody((request.approval() == TemplateApproval.APPROVED) ? template.getBody() : null);
		templates.saveAndFlush(template);
		auditService.record(ENTITY, id, AuditAction.UPDATED, "Template \"" + template.getName() + "\" "
				+ request.approval() + ((reason == null) ? "." : ": " + reason), Map.of("approval",
						request.approval().name()));
		return toResponse(template, buttons.findByTemplateIdOrderBySeqNoAsc(id));
	}

	// ---------------------------------------------------------------- checks

	/** The request after the checks: the final buttons and the examples that are really used. */
	private record Checked(List<MessageTemplateButton> buttons, Map<String, String> examples, HeaderType header) {
	}

	private Checked check(NotificationTemplateRequest request) {
		HeaderType header = (request.headerType() == null) ? HeaderType.NONE : request.headerType();
		checkMedia(header, request.mediaKey());
		return new Checked(checkButtons(request), checkExamples(request), header);
	}

	// A header needs a file of that type, saved by POST /notification-media; no header, no file.
	private void checkMedia(HeaderType header, String mediaKey) {
		if (header == HeaderType.NONE) {
			if (mediaKey != null && !mediaKey.isBlank()) {
				throw ApiException.validation("mediaKey", "must be empty when headerType is NONE");
			}
			return;
		}
		if (mediaKey == null || mediaKey.isBlank()) {
			throw ApiException.validation("mediaKey", "is needed for a " + header + " header");
		}
		mediaService.requireFile(mediaKey, MediaFileType.valueOf(header.name()), "mediaKey");
	}

	// The office's buttons, checked, then "Stop messages" last for marketing and greetings.
	private List<MessageTemplateButton> checkButtons(NotificationTemplateRequest request) {
		boolean stopList = policies.forKind(request.kind()).stopListApplies();
		List<TemplateButtonRequest> asked = (request.buttons() == null) ? List.of() : request.buttons();
		List<MessageTemplateButton> result = new ArrayList<>();
		for (int i = 0; i < asked.size(); i++) {
			TemplateButtonRequest b = asked.get(i);
			String field = "buttons[" + i + "].action";
			String action = b.action().strip();
			switch (b.type()) {
				case REPLY -> {
					ButtonAction known = replyAction(action, field);
					if (known == ButtonAction.STOP_MESSAGES) {
						if (!stopList) {
							throw ApiException.validation(field,
									"STOP_MESSAGES is only for marketing and greeting templates");
						}
						continue;   // the server adds it as the last button anyway
					}
				}
				case CALL -> action = callNumber(action, field);
				case URL -> {
					if (!action.startsWith("https://")) {
						throw ApiException.validation(field, "must be a link starting with https://");
					}
				}
			}
			result.add(button(b.type(), b.text().strip(), action));
		}
		if (stopList) {
			result.add(button(ButtonType.REPLY, stopText(request.language()), ButtonAction.STOP_MESSAGES.name()));
		}
		if (result.size() > 3) {
			throw ApiException.validation("buttons", stopList
					? "at most 2 buttons: the third is \"Stop messages\", added by the school" : "at most 3 buttons");
		}
		return result;
	}

	private static ButtonAction replyAction(String action, String field) {
		try {
			return ButtonAction.valueOf(action);
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.validation(field, "must be STOP_MESSAGES, BOOK_VISIT, GOT_IT or CALL_ME");
		}
	}

	private static String callNumber(String action, String field) {
		try {
			return PhoneNumbers.normalize(action);
		}
		catch (ApiException ex) {
			throw ApiException.validation(field, "must be a mobile number, like 98000 00100");
		}
	}

	// Every {box} of the texts needs an example; examples for boxes that are not in the texts are dropped.
	private static Map<String, String> checkExamples(NotificationTemplateRequest request) {
		Set<String> boxes = new LinkedHashSet<>(Placeholders.of(request.body()));
		boxes.addAll(Placeholders.of(request.smsBody()));
		Map<String, String> given = (request.exampleValues() == null) ? Map.of() : request.exampleValues();
		Map<String, String> examples = new LinkedHashMap<>();
		for (String box : boxes) {
			String example = given.get(box);
			if (example == null || example.isBlank()) {
				throw ApiException.validation("exampleValues", "needs an example for {" + box + "}");
			}
			examples.put(box, example.strip());
		}
		return examples;
	}

	// ---------------------------------------------------------------- helpers

	private MessageTemplate find(Long id) {
		return templates.findById(id)
			.filter(t -> t.getKind() != TemplateKind.SYSTEM)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This template does not exist."));
	}

	private static void requireActive(MessageTemplate template) {
		if (!template.isActive()) {
			throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_REPLACED",
					"A newer version of this template exists. Change that one.");
		}
	}

	private static void fill(MessageTemplate template, NotificationTemplateRequest request, Checked checked) {
		String language = (request.language() == null) ? "hi" : request.language();
		template.setLanguage(language);
		template.setProviderLanguage(language);
		String sms = (request.smsBody() == null || request.smsBody().isBlank()) ? null : request.smsBody().strip();
		template.setSmsBody(sms);
		template.setHeaderType(checked.header());
		template.setMediaKey((checked.header() == HeaderType.NONE) ? null : request.mediaKey());
		template.setExampleValues(checked.examples());
	}

	private List<MessageTemplateButton> saveButtons(Long templateId, List<MessageTemplateButton> list) {
		for (int i = 0; i < list.size(); i++) {
			list.get(i).setTemplateId(templateId);
			list.get(i).setSeqNo(i + 1);
		}
		return buttons.saveAll(list);
	}

	private static MessageTemplateButton button(ButtonType type, String text, String action) {
		MessageTemplateButton b = new MessageTemplateButton();
		b.setType(type);
		b.setText(text);
		b.setAction(action);
		return b;
	}

	private static String stopText(String language) {
		return "en".equals(language) ? "Stop messages" : "मैसेज बंद करें";
	}

	private NotificationTemplateResponse toResponse(MessageTemplate t, List<MessageTemplateButton> list) {
		List<TemplateButtonResponse> buttonList = list.stream()
			.map(b -> new TemplateButtonResponse(b.getSeqNo(), b.getType(), b.getText(), b.getAction(),
					b.getType() == ButtonType.REPLY && ButtonAction.STOP_MESSAGES.name().equals(b.getAction())))
			.toList();
		return new NotificationTemplateResponse(t.getId(), t.getName(), t.getKind(), t.getLanguage(), t.getBody(),
				t.getSmsBody(), t.getHeaderType(), t.getMediaKey(), buttonList, t.getExampleValues(), t.getApproval(),
				t.getRejectionReason(), t.getReplacesId(), t.isActive(),
				(t.getUpdatedAt() == null) ? null : t.getUpdatedAt().atZone(clock.getZone()).toOffsetDateTime());
	}

}
