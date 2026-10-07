package com.muhjain.school.trip;

import java.time.LocalDate;

import com.muhjain.school.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The attendant's phone app and the office correction screen. See docs/06-api.md, "Trips and bus status".
 * Permission: TRIPS_RECORD (attendant) or TRIPS_RECORD_ANY (office). An attendant is also limited to their own
 * route by {@link TripAccess}.
 */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

	private final MarkService markService;

	private final ManifestService manifestService;

	private final MyRouteService myRouteService;

	private final CurrentUser currentUser;

	public TripController(MarkService markService, ManifestService manifestService, MyRouteService myRouteService,
			CurrentUser currentUser) {
		this.myRouteService = myRouteService;
		this.markService = markService;
		this.manifestService = manifestService;
		this.currentUser = currentUser;
	}

	/** The attendant's own route today and the progress of the four jobs. No route today → {@code route: null}. */
	@GetMapping("/my-route")
	@PreAuthorize("hasAuthority('TRIPS_RECORD')")
	public MyRouteResponse myRoute() {
		return myRouteService.myRoute(currentUser.id());
	}

	/** Save one or many taps. Same tap twice = one row. A bad tap gets its own error, the others are saved. */
	@PostMapping("/marks")
	@PreAuthorize("hasAnyAuthority('TRIPS_RECORD', 'TRIPS_RECORD_ANY')")
	public MarksResponse marks(@Valid @RequestBody MarksRequest request) {
		return new MarksResponse(markService.apply(currentUser.id(), request.marks()));
	}

	/**
	 * The stops and children of a route for a day. An attendant gets their own route today (the route comes from
	 * the server; if they send another routeId the answer is 403 NOT_YOUR_ROUTE). The office gives routeId and date.
	 */
	@GetMapping("/manifest")
	@PreAuthorize("hasAnyAuthority('TRIPS_RECORD', 'TRIPS_RECORD_ANY')")
	public ManifestResponse manifest(@RequestParam(required = false) Long routeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return manifestService.manifest(currentUser.id(), routeId, date);
	}

}
