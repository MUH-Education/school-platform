package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.service.notification.KindPolicy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * ONE_FAMILY: a message to one family, for example "Please collect Aryan's books". An approved template, or free
 * text when the family wrote to the school in the last 24 hours.
 */
@Component
public class OneFamilyPolicy implements KindPolicy {

	private static final Set<AudienceGroup> AUDIENCES = Set.of(AudienceGroup.ONE_FAMILY);

	@Override
	public NotificationKind kind() {
		return NotificationKind.ONE_FAMILY;
	}

	@Override
	public Set<AudienceGroup> allowedAudiences() {
		return AUDIENCES;
	}

	@Override
	public boolean stopListApplies() {
		return false;
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
		return true;
	}

	// Owner only for now (docs/08-decisions.md). Target: owner, office admin, admissions desk.
	@Override
	public Set<Permission> whoMaySend() {
		return Set.of(Permission.MARKETING_SEND);
	}

	@Override
	public MessagePurpose outboxPurpose() {
		return MessagePurpose.ONE_FAMILY;
	}

}
