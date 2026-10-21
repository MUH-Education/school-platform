package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.enums.TemplateApproval;

import java.util.Optional;

/**
 * 15, every kind: the template is not APPROVED (or is gone) → TEMPLATE_NOT_APPROVED. ONE_FAMILY and STAFF may send
 * text without an approved template inside the 24-hour window; for them {@code WindowGuard} decides instead.
 */
@Component
public class TemplateApprovedGuard implements RecipientGuard {

	@Override
	public int order() {
		return 15;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return true;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		MessageTemplate template = context.template();
		if (template != null && template.getApproval() == TemplateApproval.APPROVED) {
			return Optional.empty();
		}
		if (template != null && context.policy().freeTextInsideWindow()) {
			return Optional.empty();
		}
		return Optional.of(SkipReason.TEMPLATE_NOT_APPROVED);
	}

}
