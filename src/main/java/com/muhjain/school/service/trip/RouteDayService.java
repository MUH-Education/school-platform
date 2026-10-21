package com.muhjain.school.service.trip;

import com.muhjain.school.model.dto.response.RouteChild;
import com.muhjain.school.model.dto.response.RouteDay;
import com.muhjain.school.model.dto.response.RouteSummary;
import com.muhjain.school.model.entity.BoardingEvent;
import com.muhjain.school.model.enums.EventType;
import com.muhjain.school.model.repository.BoardingEventRepository;
import com.muhjain.school.service.route.RouteService;
import com.muhjain.school.service.student.StudentQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Loads a {@link RouteDay}. The children come from {@link StudentQueryService#onRoute} (the one place that knows
 * who rides which route on which day), the stops from {@link RouteService}, the taps from our own table.
 * Example: Route 4 on 7 Oct → 19 children, 4 stops, 25 taps so far.
 */
@Service
public class RouteDayService {

	private final RouteService routeService;

	private final StudentQueryService studentQuery;

	private final BoardingEventRepository events;

	RouteDayService(RouteService routeService, StudentQueryService studentQuery, BoardingEventRepository events) {
		this.routeService = routeService;
		this.studentQuery = studentQuery;
		this.events = events;
	}

	/** Empty if the route does not exist. */
	@Transactional(readOnly = true)
	Optional<RouteDay> load(Long routeId, LocalDate date) {
		return routeService.summary(routeId).map(route -> load(route, date));
	}

	/** All routes that are turned on, oldest first. */
	@Transactional(readOnly = true)
	List<RouteDay> loadAllActive(LocalDate date) {
		return routeService.activeSummaries().stream().map(route -> load(route, date)).toList();
	}

	private RouteDay load(RouteSummary route, LocalDate date) {
		List<RouteChild> children = studentQuery.onRoute(route.id(), date);
		Map<Long, Map<EventType, BoardingEvent>> taps = new HashMap<>();
		if (!children.isEmpty()) {
			List<Long> ids = children.stream().map(RouteChild::studentId).toList();
			for (BoardingEvent event : events.findByStudentIdInAndServiceDate(ids, date)) {
				taps.computeIfAbsent(event.getStudentId(), id -> new EnumMap<>(EventType.class))
					.put(event.getEventType(), event);
			}
		}
		return new RouteDay(route.id(), route.name(), route.vehicleId(), route.schoolArrivalTime(),
				route.schoolLeaveTime(), routeService.stopTimes(route.id()), children, taps);
	}

}
