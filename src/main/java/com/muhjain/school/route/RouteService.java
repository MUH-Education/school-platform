package com.muhjain.school.route;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.vehicle.VehicleService;
import com.muhjain.school.vehicle.VehicleSummary;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes and their ordered stops. Rules 13 to 15 of docs/phases/phase-2-vehicles-staff-routes.md.
 * Other features use this service, never {@link RouteRepository}.
 * Example: Route 4 runs on Van 4 and has the stops Sadhanwas, Jakhal, Kanheri, Tohana town.
 */
@Service
public class RouteService {

	private final RouteRepository routes;

	private final RouteStopRepository stops;

	private final VehicleService vehicleService;

	private final StudentCounts studentCounts;

	private final Clock clock;

	public RouteService(RouteRepository routes, RouteStopRepository stops, VehicleService vehicleService,
			StudentCounts studentCounts, Clock clock) {
		this.routes = routes;
		this.stops = stops;
		this.vehicleService = vehicleService;
		this.studentCounts = studentCounts;
		this.clock = clock;
	}

	/** All routes, turned off ones too, oldest first, with vehicle and stops. */
	@Transactional(readOnly = true)
	public List<RouteResponse> list() {
		List<Route> all = routes.findAllByOrderByIdAsc();
		Map<Long, List<RouteStop>> stopsByRoute = stops
			.findByRouteIdInOrderByRouteIdAscSeqNoAsc(all.stream().map(Route::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(RouteStop::getRouteId));
		Map<Long, VehicleSummary> vehicles = vehicleService
			.summaries(all.stream().map(Route::getVehicleId).filter(java.util.Objects::nonNull).toList());
		LocalDate today = today();
		return all.stream()
			.map(r -> toResponse(r, stopsByRoute.getOrDefault(r.getId(), List.of()), vehicles.get(r.getVehicleId()),
					today))
			.toList();
	}

	/** @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public RouteResponse get(Long id) {
		return response(find(id));
	}

	/**
	 * Rule 13. Example: "Route 4" on Van 4. A second active route on Van 4 is refused.
	 *
	 * @throws ApiException 400 VALIDATION (unknown vehicle), 409 ROUTE_NAME_ALREADY_USED, VEHICLE_HAS_ROUTE,
	 * VEHICLE_INACTIVE
	 */
	@Transactional
	public RouteResponse create(CreateRouteRequest request) {
		String name = NameKeys.tidy(request.name());
		checkName(name, 0L);
		checkVehicle(request.vehicleId(), true, 0L);
		Route route = save(new Route(name, request.vehicleId()));
		return response(route);
	}

	/**
	 * Change name, vehicle, or turn the route off or on.
	 * Turning off follows the same rule as DELETE.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 ROUTE_NAME_ALREADY_USED, VEHICLE_HAS_ROUTE, VEHICLE_INACTIVE,
	 * ROUTE_HAS_STUDENTS
	 */
	@Transactional
	public RouteResponse update(Long id, UpdateRouteRequest request) {
		Route route = find(id);
		String name = NameKeys.tidy(request.name());
		checkName(name, id);
		boolean active = request.active();
		if (route.isActive() && !active) {
			checkNoChildren(route);
		}
		checkVehicle(request.vehicleId(), active, id);
		route.setName(name);
		route.setVehicleId(request.vehicleId());
		route.setActive(active);
		return response(save(route));
	}

	/**
	 * Turn off. Routes are never deleted (B10). Turning off a route that is already off changes nothing.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 ROUTE_HAS_STUDENTS
	 */
	@Transactional
	public void turnOff(Long id) {
		Route route = find(id);
		if (route.isActive()) {
			checkNoChildren(route);
			route.setActive(false);
		}
	}

	/**
	 * Rule 14 and 15. The list is the whole ordered list of stops. The server sets {@code seq_no} 1, 2, 3 from the
	 * order. A stop with an id is kept, one without is new, one missing from the list is removed.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION, 409 STOP_HAS_STUDENTS
	 */
	@Transactional
	public RouteResponse saveStops(Long routeId, List<StopRequest> requested) {
		Route route = find(routeId);
		Map<Long, RouteStop> existing = stops.findByRouteIdOrderBySeqNoAsc(routeId)
			.stream()
			.collect(Collectors.toMap(RouteStop::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
		Set<Long> kept = checkStopList(requested, existing.keySet());

		List<RouteStop> removed = existing.values().stream().filter(s -> !kept.contains(s.getId())).toList();
		if (!removed.isEmpty()) {
			Map<Long, Integer> children = studentCounts.childrenByStop(routeId, today());
			for (RouteStop stop : removed) {
				int count = children.getOrDefault(stop.getId(), 0);
				if (count > 0) {
					throw new ApiException(HttpStatus.CONFLICT, "STOP_HAS_STUDENTS",
							count + " children board at " + stop.getName() + ". Move them to another stop first.");
				}
			}
			stops.deleteAll(removed);
		}

		int seqNo = 1;
		List<RouteStop> toSave = new ArrayList<>();
		for (StopRequest item : requested) {
			String name = NameKeys.tidy(item.name());
			if (item.id() == null) {
				toSave.add(new RouteStop(routeId, name, seqNo, item.morningTime(), item.eveningTime()));
			}
			else {
				RouteStop stop = existing.get(item.id());
				stop.setName(name);
				stop.setSeqNo(seqNo);
				stop.setMorningTime(item.morningTime());
				stop.setEveningTime(item.eveningTime());
				toSave.add(stop);
			}
			seqNo++;
		}
		stops.saveAll(toSave);
		stops.flush();
		return response(route);
	}

	/** The active route of a vehicle, if any. Example: Van 4 → Route 4. */
	@Transactional(readOnly = true)
	public Optional<RouteRef> activeRouteOfVehicle(Long vehicleId) {
		return routes.findByVehicleIdAndActiveTrue(vehicleId).map(r -> new RouteRef(r.getId(), r.getName()));
	}

	/** Same for many vehicles. A vehicle with no active route is not in the map. */
	@Transactional(readOnly = true)
	public Map<Long, RouteRef> activeRoutesByVehicle(Collection<Long> vehicleIds) {
		if (vehicleIds.isEmpty()) {
			return Map.of();
		}
		return routes.findByVehicleIdInAndActiveTrue(vehicleIds)
			.stream()
			.collect(Collectors.toMap(Route::getVehicleId, r -> new RouteRef(r.getId(), r.getName())));
	}

	private Route find(Long id) {
		return routes.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This route does not exist."));
	}

	private RouteResponse response(Route route) {
		VehicleSummary vehicle = (route.getVehicleId() == null) ? null
				: vehicleService.summaries(List.of(route.getVehicleId())).get(route.getVehicleId());
		return toResponse(route, stops.findByRouteIdOrderBySeqNoAsc(route.getId()), vehicle, today());
	}

	private RouteResponse toResponse(Route route, List<RouteStop> routeStops, VehicleSummary vehicle,
			LocalDate today) {
		Map<Long, Integer> children = routeStops.isEmpty() ? Map.of()
				: studentCounts.childrenByStop(route.getId(), today);
		List<StopResponse> items = routeStops.stream()
			.map(s -> StopResponse.of(s, children.getOrDefault(s.getId(), 0)))
			.toList();
		return new RouteResponse(route.getId(), route.getName(), route.isActive(), RouteResponse.VehicleRef.of(vehicle),
				items);
	}

	private void checkName(String name, Long exceptId) {
		if (routes.existsByNameKey(NameKeys.key(name), exceptId)) {
			throw nameUsed();
		}
	}

	// The vehicle must exist. A route that is on must have a vehicle that is on, and no other route on it (rule 13).
	private void checkVehicle(Long vehicleId, boolean routeIsActive, Long exceptRouteId) {
		if (vehicleId == null) {
			return;
		}
		VehicleSummary vehicle = vehicleService.summaries(List.of(vehicleId)).get(vehicleId);
		if (vehicle == null) {
			throw ApiException.validation("vehicleId", "does not exist");
		}
		if (!routeIsActive) {
			return;
		}
		if (!vehicle.active()) {
			throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_INACTIVE",
					vehicle.name() + " is turned off. Turn it on first.");
		}
		routes.findByVehicleIdAndActiveTrue(vehicleId)
			.filter(other -> !other.getId().equals(exceptRouteId))
			.ifPresent(other -> {
				throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_HAS_ROUTE",
						vehicle.name() + " already runs " + other.getName() + ".");
			});
	}

	private void checkNoChildren(Route route) {
		int children = studentCounts.childrenOnRoute(route.getId(), today());
		if (children > 0) {
			throw new ApiException(HttpStatus.CONFLICT, "ROUTE_HAS_STUDENTS",
					children + " children ride on " + route.getName() + ". Move them to another route first.");
		}
	}

	// The list must make sense: names given, ids of this route, no id twice. Returns the ids that are kept.
	private static Set<Long> checkStopList(List<StopRequest> requested, Set<Long> existingIds) {
		Set<Long> kept = new HashSet<>();
		for (int i = 0; i < requested.size(); i++) {
			StopRequest item = requested.get(i);
			if (item == null || item.name() == null || item.name().isBlank()) {
				throw ApiException.validation("stops[" + i + "].name", "must not be blank");
			}
			if (NameKeys.tidy(item.name()).length() > 80) {
				throw ApiException.validation("stops[" + i + "].name", "must be at most 80 characters");
			}
			if (item.id() != null) {
				if (!existingIds.contains(item.id())) {
					throw ApiException.validation("stops[" + i + "].id", "is not a stop of this route");
				}
				if (!kept.add(item.id())) {
					throw ApiException.validation("stops[" + i + "].id", "is in the list twice");
				}
			}
		}
		return kept;
	}

	// The checks above can miss a route saved at the same moment. The unique indexes still catch it.
	private Route save(Route route) {
		try {
			return routes.saveAndFlush(route);
		}
		catch (DataIntegrityViolationException ex) {
			String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
			if (cause.contains("route_vehicle_active_uk")) {
				throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_HAS_ROUTE",
						"This vehicle already runs another route.");
			}
			if (cause.contains("route_name_uk")) {
				throw nameUsed();
			}
			throw ex;
		}
	}

	private static ApiException nameUsed() {
		return new ApiException(HttpStatus.CONFLICT, "ROUTE_NAME_ALREADY_USED", "Another route already has this name.");
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

}
