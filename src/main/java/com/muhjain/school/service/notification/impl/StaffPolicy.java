package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.service.notification.KindPolicy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * STAFF: "Staff meeting at 3 pm", "Route 4 crew: new stop from Monday". Never touched by the stop list. Free text is
 * allowed inside the 24-hour window.
 */
@Component
public class StaffPolicy implements KindPolicy {

	private static final Set<AudienceGroup> AUDIENCES = Set.of(AudienceGroup.STAFF_ALL, AudienceGroup.DRIVERS,
			AudienceGroup.ATTENDANTS, AudienceGroup.TEACHERS, AudienceGroup.ROUTE_CREW, AudienceGroup.ONE_STAFF);

	@Override
	public NotificationKind kind() {
		return NotificationKind.STAFF;
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

	// Owner only for now (docs/08-decisions.md). Target: owner, office admin; transport in-charge for transport staff.
	@Override
	public Set<Permission> whoMaySend() {
		return Set.of(Permission.MARKETING_SEND);
	}

	@Override
	public MessagePurpose outboxPurpose() {
		return MessagePurpose.STAFF;
	}

}
