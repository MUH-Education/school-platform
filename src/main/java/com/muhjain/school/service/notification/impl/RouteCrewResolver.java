package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Crew;
import com.muhjain.school.model.dto.response.CrewMember;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.RouteSummary;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.route.RouteService;
import com.muhjain.school.service.staff.AssignmentService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * ROUTE_CREW: the driver, attendant and helper of the vehicle of these routes, today (a replacement counts, the
 * person on leave does not). Extra value {@code Route}. Example: Route 4 → Jagdish (driver) and Balwan (attendant).
 */
@Component
public class RouteCrewResolver implements RecipientResolver {

	private final RouteService routeService;

	private final AssignmentService assignmentService;

	private final Clock clock;

	public RouteCrewResolver(RouteService routeService, AssignmentService assignmentService, Clock clock) {
		this.routeService = routeService;
		this.assignmentService = assignmentService;
		this.clock = clock;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.ROUTE_CREW);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		LocalDate today = LocalDate.now(clock);
		List<Recipient> result = new ArrayList<>();
		for (Long routeId : filter.routeIds().stream().distinct().toList()) {
			Optional<RouteSummary> route = routeService.summary(routeId);
			if (route.isEmpty() || route.get().vehicleId() == null) {
				continue;
			}
			Crew crew = assignmentService.onDate(route.get().vehicleId(), today);
			Stream.of(crew.driver(), crew.attendant(), crew.helper())
				.filter(m -> m != null)
				.map(m -> member(m, route.get().name(), group))
				.forEach(result::add);
		}
		return result;
	}

	private static Recipient member(CrewMember m, String routeName, AudienceGroup group) {
		return StaffResolver.recipient(m.staffId(), m.name(), m.phone(), group, Map.of("Route", routeName));
	}

}
