package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.RouteChild;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.notification.FamilyRecipients;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.route.RouteService;
import com.muhjain.school.service.student.StudentQueryService;
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
 * ROUTE: the families of the children on these routes today (for URGENT "bus late"). No route picked = nobody.
 * Filter: classes. Extra value {@code Route}. Example: route 4 → the 19 families on Route 4,
 * {@code {Route} = "Route 4"}.
 */
@Component
public class RouteResolver implements RecipientResolver {

	private final StudentQueryService studentQuery;

	private final RouteService routeService;

	private final FamilyRecipients families;

	private final Clock clock;

	public RouteResolver(StudentQueryService studentQuery, RouteService routeService, FamilyRecipients families,
			Clock clock) {
		this.studentQuery = studentQuery;
		this.routeService = routeService;
		this.families = families;
		this.clock = clock;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.ROUTE);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		if (filter.routeIds().isEmpty()) {
			return List.of();
		}
		LocalDate today = LocalDate.now(clock);
		Map<Long, String> routeNames = routeService.routeNames(filter.routeIds());
		Map<Long, String> routeOfChild = new HashMap<>();
		List<FamilyRecipients.Child> children = new ArrayList<>();
		for (Long routeId : filter.routeIds().stream().distinct().toList()) {
			for (RouteChild child : studentQuery.onRoute(routeId, today)) {
				if (!filter.classes().isEmpty() && !filter.classes().contains(child.className())) {
					continue;
				}
				if (routeOfChild.putIfAbsent(child.studentId(), routeNames.getOrDefault(routeId, "")) == null) {
					children.add(new FamilyRecipients.Child(child.studentId(), child.name(), child.className()));
				}
			}
		}
		return families.of(children, group, family -> Map.of("Route", RecipientText
			.join(family.stream().map(c -> routeOfChild.get(c.studentId())).distinct().toList())));
	}

}
