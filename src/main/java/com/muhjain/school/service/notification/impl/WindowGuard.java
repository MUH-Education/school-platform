package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.enums.TemplateApproval;
import com.muhjain.school.model.repository.InboundMessageRepository;

import java.time.Duration;
import java.util.Optional;

/**
 * 16, ONE_FAMILY and STAFF with a template that is not approved (free text): WhatsApp allows it only when the person
 * wrote to the school in the last 24 hours → else OUTSIDE_WINDOW.
 * Example: Mrs Punia wrote "Is the bus late?" at 7:50; a free-text answer at 8:05 may go. The same text to a family
 * who never wrote is skipped.
 */
@Component
public class WindowGuard implements RecipientGuard {

	private final InboundMessageRepository inbound;

	public WindowGuard(InboundMessageRepository inbound) {
		this.inbound = inbound;
	}

	@Override
	public int order() {
		return 16;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return kind == NotificationKind.ONE_FAMILY || kind == NotificationKind.STAFF;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		if (context.template() != null && context.template().getApproval() == TemplateApproval.APPROVED) {
			return Optional.empty();
		}
		boolean wrote = inbound.existsByPhoneAndReceivedAtAfter(candidate.recipient().phone(),
				context.now().minus(Duration.ofHours(24)));
		return wrote ? Optional.empty() : Optional.of(SkipReason.OUTSIDE_WINDOW);
	}

}
