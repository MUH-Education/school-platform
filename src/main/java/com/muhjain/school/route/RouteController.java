package com.muhjain.school.route;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Routes screens. Reading needs ROUTES_VIEW, changing needs ROUTES_EDIT.
 * Routes are never deleted: DELETE turns one off.
 */
@Tag(name = "Routes")
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

	private final RouteService routeService;

	private final LoadBoardService loadBoardService;

	public RouteController(RouteService routeService, LoadBoardService loadBoardService) {
		this.routeService = routeService;
		this.loadBoardService = loadBoardService;
	}

	@Operation(summary = "List routes")
	@GetMapping
	@PreAuthorize("hasAuthority('ROUTES_VIEW')")
	public List<RouteResponse> list() {
		return routeService.list();
	}

	/** The Routes and load screen: every active route with children, seats, load and cost, and the fleet totals. */
	@Operation(summary = "Seats used on each route")
	@GetMapping("/load-board")
	@PreAuthorize("hasAuthority('ROUTES_VIEW')")
	public LoadBoardResponse loadBoard() {
		return loadBoardService.board();
	}

	@Operation(summary = "Add a route")
	@PostMapping
	@PreAuthorize("hasAuthority('ROUTES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public RouteResponse create(@Valid @RequestBody CreateRouteRequest request) {
		return routeService.create(request);
	}

	@Operation(summary = "Show one route")
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('ROUTES_VIEW')")
	public RouteResponse get(@PathVariable Long id) {
		return routeService.get(id);
	}

	@Operation(summary = "Change a route")
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ROUTES_EDIT')")
	public RouteResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRouteRequest request) {
		return routeService.update(id, request);
	}

	@Operation(summary = "Turn a route off")
	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('ROUTES_EDIT')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void turnOff(@PathVariable Long id) {
		routeService.turnOff(id);
	}

	/** The whole ordered list of stops. The order of the list is the morning order. */
	@Operation(summary = "Save the stops of a route")
	@PutMapping("/{id}/stops")
	@PreAuthorize("hasAuthority('ROUTES_EDIT')")
	public RouteResponse saveStops(@PathVariable Long id, @RequestBody List<StopRequest> stops) {
		return routeService.saveStops(id, stops);
	}

}
