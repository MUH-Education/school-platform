package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.entity.Notification;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.SkipReason;
import com.muhjain.school.service.notification.Candidate;
import com.muhjain.school.service.notification.GuardContext;
import com.muhjain.school.service.notification.RecipientGuard;
import org.springframework.stereotype.Component;

import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.StudentBus;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.student.StudentQueryService;
import com.muhjain.school.service.trip.SchoolDayService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 51, URGENT and STAFF, for people found through a route (ROUTE, ROUTE_CREW) → NOT_ON_ROUTE_TODAY when there is no
 * bus today (Sunday or a holiday), or when none of the family's children is on one of the picked routes today.
 * Example: "Route 4 is 20 minutes late" — Aryan moved to Route 9 this morning, so his family is skipped.
 */
@Component
public class RouteTodayGuard implements RecipientGuard {

	private final SchoolDayService schoolDays;

	private final StudentQueryService studentQuery;

	public RouteTodayGuard(SchoolDayService schoolDays, StudentQueryService studentQuery) {
		this.schoolDays = schoolDays;
		this.studentQuery = studentQuery;
	}

	@Override
	public int order() {
		return 51;
	}

	@Override
	public boolean appliesTo(NotificationKind kind) {
		return kind == NotificationKind.URGENT || kind == NotificationKind.STAFF;
	}

	@Override
	public Optional<SkipReason> check(Candidate candidate, Notification notification, GuardContext context) {
		Recipient r = candidate.recipient();
		if (r.group() != AudienceGroup.ROUTE && r.group() != AudienceGroup.ROUTE_CREW) {
			return Optional.empty();
		}
		boolean schoolDay = context.cached("schoolDay", () -> schoolDays.isSchoolDay(context.today()));
		if (!schoolDay) {
			return Optional.of(SkipReason.NOT_ON_ROUTE_TODAY);
		}
		if (r.group() == AudienceGroup.ROUTE_CREW) {
			return Optional.empty();
		}
		List<Long> routes = notification.getAudience().filter().routeIds();
		Map<Long, StudentBus> bus = studentQuery.busOn(r.studentIds(), context.today());
		boolean onRoute = bus.values()
			.stream()
			.anyMatch(b -> b.active() && b.routeId() != null && routes.contains(b.routeId()));
		return onRoute ? Optional.empty() : Optional.of(SkipReason.NOT_ON_ROUTE_TODAY);
	}

}
