package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.StudentBasics;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.student.StudentQueryService;

import java.util.List;
import java.util.Optional;

/**
 * 52, NOTICE and URGENT, for families picked with a class filter: none of the children is in those classes today
 * (the class changed since the draft) → FILTER_NO_LONGER_MATCHES.
 * Example: "Class 10 exam on Monday" drafted on Friday; Kavya moved to Class 11 on Saturday → skipped.
 */
@Component
public class ClassFilterGuard implements RecipientGuard {

	private final StudentQueryService studentQuery;

	public ClassFilterGuard(StudentQueryService studentQuery) {
		this.studentQuery = studentQuery;
	}

	@Override
	public int order() {
		return 52;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return kind == NotificationKind.NOTICE || kind == NotificationKind.URGENT;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		Recipient r = candidate.recipient();
		List<String> classes = notification.getAudience().filter().classes();
		if (classes.isEmpty() || (r.group() != AudienceGroup.CURRENT_PARENTS && r.group() != AudienceGroup.ROUTE)) {
			return Optional.empty();
		}
		boolean matches = studentQuery.basics(r.studentIds())
			.values()
			.stream()
			.map(StudentBasics::className)
			.anyMatch(classes::contains);
		return matches ? Optional.empty() : Optional.of(SkipReason.FILTER_NO_LONGER_MATCHES);
	}

}
