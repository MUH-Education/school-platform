package com.muhjain.school.route;

import java.util.List;

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
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

	private final RouteService routeService;

	public RouteController(RouteService routeService) {
		this.routeService = routeService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('ROUTES_VIEW')")
	public List<RouteResponse> list() {
		return routeService.list();
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ROUTES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public RouteResponse create(@Valid @RequestBody CreateRouteRequest request) {
		return routeService.create(request);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('ROUTES_VIEW')")
	public RouteResponse get(@PathVariable Long id) {
		return routeService.get(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ROUTES_EDIT')")
	public RouteResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRouteRequest request) {
		return routeService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('ROUTES_EDIT')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void turnOff(@PathVariable Long id) {
		routeService.turnOff(id);
	}

	/** The whole ordered list of stops. The order of the list is the morning order. */
	@PutMapping("/{id}/stops")
	@PreAuthorize("hasAuthority('ROUTES_EDIT')")
	public RouteResponse saveStops(@PathVariable Long id, @RequestBody List<StopRequest> stops) {
		return routeService.saveStops(id, stops);
	}

}
