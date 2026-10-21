package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.service.notification.KindPolicies;
import com.muhjain.school.service.notification.StopListService;

import java.util.Optional;
import java.util.Set;

/**
 * 13, MARKETING and GREETING (the kinds whose policy says so): the family said stop → STOPPED. By phone, so the
 * same family as an enquiry is stopped too. Staff are never stopped.
 */
@Component
public class StopListGuard implements RecipientGuard {

	private final StopListService stopList;

	private final KindPolicies policies;

	public StopListGuard(StopListService stopList, KindPolicies policies) {
		this.stopList = stopList;
		this.policies = policies;
	}

	@Override
	public int order() {
		return 13;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return policies.forKind(kind).stopListApplies();
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		if (candidate.recipient().type() == RecipientType.STAFF) {
			return Optional.empty();
		}
		Set<String> stopped = context.cached("stoppedPhones", stopList::stoppedPhones);
		return stopped.contains(candidate.recipient().phone()) ? Optional.of(SkipReason.STOPPED) : Optional.empty();
	}

}
