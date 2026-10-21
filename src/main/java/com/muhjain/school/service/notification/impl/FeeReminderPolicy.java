package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.service.notification.KindPolicy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * FEE_REMINDER: "Fee for 2 months is pending". Only to families with dues; the amount is read live at send time,
 * never from the draft. The stop list does not apply (it is not marketing).
 */
@Component
public class FeeReminderPolicy implements KindPolicy {

	private static final Set<AudienceGroup> AUDIENCES = Set.of(AudienceGroup.FAMILIES_WITH_DUES);

	@Override
	public NotificationKind kind() {
		return NotificationKind.FEE_REMINDER;
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
		return false;
	}

	// Owner only for now (docs/08-decisions.md). Target: owner, office admin.
	@Override
	public Set<Permission> whoMaySend() {
		return Set.of(Permission.MARKETING_SEND);
	}

	@Override
	public MessagePurpose outboxPurpose() {
		return MessagePurpose.FEE_REMINDER;
	}

}
