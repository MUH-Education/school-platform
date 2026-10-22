package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.UntappedChild;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.notification.FamilyRecipients;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.trip.RouteDayService;
import com.muhjain.school.service.trip.SchoolDayService;
import com.muhjain.school.util.RecipientText;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BUS_CHILDREN_WITHOUT_TAP_TODAY: the families of the children on a bus today who have no morning tap (for the
 * 8:30 "child did not board" rule). Nobody on a day without school, and nobody from a route whose attendant has not
 * tapped anyone yet ({@link RouteDayService#withoutMorningTap}). Filters: classes, routes. Extra value {@code Route}.
 * Example: Route 4, 17 of 19 tapped → the 2 other families, {@code {Route} = "Route 4 · Jakhal"}.
 */
@Component
public class BusChildrenWithoutTapTodayResolver implements RecipientResolver {

	private final RouteDayService routeDays;

	private final SchoolDayService schoolDays;

	private final FamilyRecipients families;

	private final Clock clock;

	public BusChildrenWithoutTapTodayResolver(RouteDayService routeDays, SchoolDayService schoolDays,
			FamilyRecipients families, Clock clock) {
		this.routeDays = routeDays;
		this.schoolDays = schoolDays;
		this.families = families;
		this.clock = clock;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.BUS_CHILDREN_WITHOUT_TAP_TODAY);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		LocalDate today = LocalDate.now(clock);
		if (!schoolDays.isSchoolDay(today)) {
			return List.of();
		}
		Map<Long, String> routeOfChild = new HashMap<>();
		List<FamilyRecipients.Child> children = new ArrayList<>();
		for (UntappedChild child : routeDays.withoutMorningTap(today)) {
			if (!filter.classes().isEmpty() && !filter.classes().contains(child.className())) {
				continue;
			}
			if (!filter.routeIds().isEmpty() && !filter.routeIds().contains(child.routeId())) {
				continue;
			}
			if (routeOfChild.putIfAbsent(child.studentId(), child.routeName()) == null) {
				children.add(new FamilyRecipients.Child(child.studentId(), child.name(), child.className()));
			}
		}
		return families.of(children, group, family -> Map.of("Route", RecipientText
			.join(family.stream().map(c -> routeOfChild.get(c.studentId())).distinct().toList())));
	}

}
