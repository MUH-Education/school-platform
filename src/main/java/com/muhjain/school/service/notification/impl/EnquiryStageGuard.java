package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.enums.EnquiryStatus;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.service.enquiry.EnquiryService;

import java.util.Optional;

/**
 * 53, MARKETING and GREETING, for enquiries: the enquiry was ADMITTED since it was picked → NOW_ADMITTED. The family
 * is a current parent now and is counted there. Example: "Admissions open" to open enquiries; the Malik child was
 * admitted this morning → skipped as an enquiry.
 */
@Component
public class EnquiryStageGuard implements RecipientGuard {

	private final EnquiryService enquiryService;

	public EnquiryStageGuard(EnquiryService enquiryService) {
		this.enquiryService = enquiryService;
	}

	@Override
	public int order() {
		return 53;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return kind == NotificationKind.MARKETING || kind == NotificationKind.GREETING;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		if (candidate.recipient().type() != RecipientType.ENQUIRY) {
			return Optional.empty();
		}
		boolean admitted = enquiryService.statusOf(candidate.recipient().personId())
			.map(s -> s == EnquiryStatus.ADMITTED)
			.orElse(false);
		return admitted ? Optional.of(SkipReason.NOW_ADMITTED) : Optional.empty();
	}

}
