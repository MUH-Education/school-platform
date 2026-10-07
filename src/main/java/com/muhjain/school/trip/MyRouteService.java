package com.muhjain.school.trip;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import com.muhjain.school.route.AttendantRoute;
import com.muhjain.school.route.AttendantRouteService;
import com.muhjain.school.student.RouteChild;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code GET /trips/my-route}: the route of the logged-in attendant today, from the server's own records. */
@Service
public class MyRouteService {

	private final AttendantRouteService attendantRoutes;

	private final RouteDayService routeDays;

	private final Clock clock;

	public MyRouteService(AttendantRouteService attendantRoutes, RouteDayService routeDays, Clock clock) {
		this.attendantRoutes = attendantRoutes;
		this.routeDays = routeDays;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public MyRouteResponse myRoute(Long userId) {
		LocalDate today = LocalDate.now(clock);
		Optional<AttendantRoute> own = attendantRoutes.routeFor(userId, today);
		if (own.isEmpty()) {
			return new MyRouteResponse(today, null, null, null);
		}
		AttendantRoute route = own.get();
		RouteDay day = routeDays.load(route.routeId(), today).orElseThrow();
		JobsResponse jobs = new JobsResponse(count(day, EventType.BOARDED_MORNING),
				count(day, EventType.REACHED_SCHOOL), count(day, EventType.BOARDED_EVENING),
				count(day, EventType.REACHED_HOME));
		return new MyRouteResponse(today, new MyRouteResponse.Route(route.routeId(), route.routeName(),
				route.vehicleName()), day.children().size(), jobs);
	}

	private static JobCount count(RouteDay day, EventType type) {
		int done = 0;
		int absent = 0;
		int notTravelling = 0;
		for (RouteChild child : day.children()) {
			BoardingEvent tap = day.tap(child.studentId(), type);
			if (tap == null) {
				continue;
			}
			switch (tap.getOutcome()) {
				case DONE -> done++;
				case ABSENT -> absent++;
				case NOT_TRAVELLING -> notTravelling++;
				default -> {
				}
			}
		}
		return new JobCount(done, absent, notTravelling, day.children().size() - done - absent - notTravelling);
	}

}
