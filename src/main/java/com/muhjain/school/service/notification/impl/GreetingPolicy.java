package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.service.notification.KindPolicy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * GREETING: "Happy Diwali". Same audiences as marketing, and WhatsApp counts it as marketing, so the stop list applies
 * and the outbox purpose is MARKETING.
 */
@Component
public class GreetingPolicy implements KindPolicy {

	private static final Set<AudienceGroup> AUDIENCES = Set.of(AudienceGroup.CURRENT_PARENTS,
			AudienceGroup.ENQUIRY_OPEN, AudienceGroup.ENQUIRY_LOST, AudienceGroup.STAFF_ALL, AudienceGroup.DRIVERS,
			AudienceGroup.ATTENDANTS, AudienceGroup.TEACHERS);

	@Override
	public NotificationKind kind() {
		return NotificationKind.GREETING;
	}

	@Override
	public Set<AudienceGroup> allowedAudiences() {
		return AUDIENCES;
	}

	@Override
	public boolean stopListApplies() {
		return true;
	}

	@Override
	public boolean ignoresQuietHours() {
		return false;
	}

	@Override
	public boolean alsoSmsWhenOnWhatsApp() {
		return false;
	}

	@Override
	public boolean freeTextInsideWindow() {
		return false;
	}

	// Owner only for now (docs/08-decisions.md). Target: owner, office admin.
	@Override
	public Set<Permission> whoMaySend() {
		return Set.of(Permission.MARKETING_SEND);
	}

	@Override
	public MessagePurpose outboxPurpose() {
		return MessagePurpose.MARKETING;
	}

}
