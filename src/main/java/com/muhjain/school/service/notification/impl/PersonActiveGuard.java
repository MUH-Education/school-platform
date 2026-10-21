package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.EnquiryStatus;
import com.muhjain.school.service.enquiry.EnquiryService;
import com.muhjain.school.service.staff.StaffService;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 12, every kind: the person is no longer there → PERSON_INACTIVE. A family whose every child has left or finished;
 * an employee who is turned off; an enquiry that moved out of the stage it was picked for (an ADMITTED enquiry is
 * left to {@code EnquiryStageGuard}, which says NOW_ADMITTED).
 */
@Component
public class PersonActiveGuard implements RecipientGuard {

	private static final Set<EnquiryStatus> OPEN = EnumSet.of(EnquiryStatus.NEW, EnquiryStatus.CONTACTED,
			EnquiryStatus.VISITED, EnquiryStatus.APPLIED);

	private final StaffService staffService;

	private final EnquiryService enquiryService;

	public PersonActiveGuard(StaffService staffService, EnquiryService enquiryService) {
		this.staffService = staffService;
		this.enquiryService = enquiryService;
	}

	@Override
	public int order() {
		return 12;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return true;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		Recipient r = candidate.recipient();
		boolean active = switch (r.type()) {
			case GUARDIAN -> !r.studentIds().isEmpty();
			case STAFF -> staffService.isActive(r.personId());
			case ENQUIRY -> enquiryService.statusOf(r.personId()).map(s -> stillInStage(s, r.group())).orElse(false);
		};
		return active ? Optional.empty() : Optional.of(SkipReason.PERSON_INACTIVE);
	}

	private static boolean stillInStage(EnquiryStatus status, AudienceGroup group) {
		if (status == EnquiryStatus.ADMITTED) {
			return true;
		}
		if (group == AudienceGroup.ENQUIRY_LOST) {
			return status == EnquiryStatus.LOST;
		}
		return group != AudienceGroup.ENQUIRY_OPEN || OPEN.contains(status);
	}

}
