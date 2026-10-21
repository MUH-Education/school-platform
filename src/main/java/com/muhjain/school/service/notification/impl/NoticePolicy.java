package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.service.notification.KindPolicy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * NOTICE: "PTM on Saturday", "School closed on Monday". To current parents (with filters) or one family. The stop list
 * does not apply: a notice is not marketing.
 */
@Component
public class NoticePolicy implements KindPolicy {

	private static final Set<AudienceGroup> AUDIENCES = Set.of(AudienceGroup.CURRENT_PARENTS,
			AudienceGroup.ONE_FAMILY);

	@Override
	public NotificationKind kind() {
		return NotificationKind.NOTICE;
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

	// Owner only for now (docs/08-decisions.md). Target: owner, office admin, admissions desk.
	@Override
	public Set<Permission> whoMaySend() {
		return Set.of(Permission.MARKETING_SEND);
	}

	@Override
	public MessagePurpose outboxPurpose() {
		return MessagePurpose.NOTICE;
	}

}
