package com.muhjain.school.trip;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.route.AttendantRoute;
import com.muhjain.school.route.StopTimes;
import com.muhjain.school.student.RouteChild;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The manifest (rules 11 to 13). Stops in morning order. Under each stop the children who are on the route <b>on
 * that date</b>, with their four events. No parent phone numbers (C10).
 * Example: a child whose bus starts tomorrow is not in today's manifest.
 */
@Service
public class ManifestService {

	private final TripAccess tripAccess;

	private final RouteDayService routeDays;

	private final Clock clock;

	public ManifestService(TripAccess tripAccess, RouteDayService routeDays, Clock clock) {
		this.tripAccess = tripAccess;
		this.routeDays = routeDays;
		this.clock = clock;
	}

	/**
	 * @param userId the logged-in user, from the token
	 * @param routeId an attendant may leave it out (their own route is used) or give their own route; the office
	 * must give it
	 * @param date default today
	 * @throws ApiException 403 NOT_YOUR_ROUTE / DATE_NOT_ALLOWED, 400 VALIDATION, 404 NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public ManifestResponse manifest(Long userId, Long routeId, LocalDate date) {
		LocalDate day = (date != null) ? date : LocalDate.now(clock);
		TripAccess.Access access = tripAccess.forUser(userId);
		Long wanted = routeId;
		if (wanted == null) {
			if (access.isOffice()) {
				throw ApiException.validation("routeId", "is required");
			}
			wanted = access.ownRoute(day)
				.map(AttendantRoute::routeId)
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, TripAccess.NOT_YOUR_ROUTE,
						"This is not your route today."));
		}
		access.requireRead(wanted, day);
		RouteDay routeDay = routeDays.load(wanted, day)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This route does not exist."));
		return build(routeDay, day);
	}

	private ManifestResponse build(RouteDay routeDay, LocalDate day) {
		Map<Long, List<RouteChild>> byStop = routeDay.children().stream().collect(Collectors.groupingBy(RouteChild::stopId));
		List<ManifestStop> stops = new ArrayList<>();
		for (StopTimes stop : routeDay.stops()) {
			List<ManifestChild> children = byStop.getOrDefault(stop.id(), List.of())
				.stream()
				.map(child -> new ManifestChild(child.studentId(), child.name(), child.admissionNo(),
						child.gender().name(), child.className(), child.section(),
						EventsResponse.of(routeDay.events().get(child.studentId()), clock.getZone())))
				.toList();
			stops.add(new ManifestStop(stop.id(), stop.name(), stop.seqNo(), stop.morningTime(), stop.eveningTime(),
					children));
		}
		return new ManifestResponse(routeDay.routeId(), routeDay.routeName(), day, stops);
	}

}
