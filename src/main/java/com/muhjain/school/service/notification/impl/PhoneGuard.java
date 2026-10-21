package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/** 10, every kind: no phone, or not an Indian mobile number (+91 then 6, 7, 8 or 9) → NO_PHONE. */
@Component
public class PhoneGuard implements RecipientGuard {

	private static final Pattern MOBILE = Pattern.compile("\\+91[6-9][0-9]{9}");

	@Override
	public int order() {
		return 10;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return true;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		String phone = candidate.recipient().phone();
		return (phone != null && MOBILE.matcher(phone).matches()) ? Optional.empty() : Optional.of(SkipReason.NO_PHONE);
	}

}
