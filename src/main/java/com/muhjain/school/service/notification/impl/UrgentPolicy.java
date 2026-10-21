package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.service.notification.KindPolicy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * URGENT: "Bus 4 is 20 minutes late", "School closed today, heavy rain". Sent at once, also at night, on WhatsApp and
 * SMS both. The stop list does not apply.
 */
@Component
public class UrgentPolicy implements KindPolicy {

	private static final Set<AudienceGroup> AUDIENCES = Set.of(AudienceGroup.CURRENT_PARENTS, AudienceGroup.ROUTE,
			AudienceGroup.STAFF_ALL, AudienceGroup.DRIVERS, AudienceGroup.ATTENDANTS, AudienceGroup.TEACHERS,
			AudienceGroup.ROUTE_CREW);

	@Override
	public NotificationKind kind() {
		return NotificationKind.URGENT;
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
		return true;
	}

	@Override
	public boolean alsoSmsWhenOnWhatsApp() {
		return true;
	}

	@Override
	public boolean freeTextInsideWindow() {
		return false;
	}

	// Owner only for now (docs/08-decisions.md). Target: owner, office admin; transport in-charge for ROUTE only.
	@Override
	public Set<Permission> whoMaySend() {
		return Set.of(Permission.MARKETING_SEND);
	}

	@Override
	public MessagePurpose outboxPurpose() {
		return MessagePurpose.URGENT;
	}

}
