package com.muhjain.school.trip;

import com.muhjain.school.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

	private final CurrentUser currentUser;

	public TripController(MarkService markService, CurrentUser currentUser) {
		this.markService = markService;
		this.currentUser = currentUser;
	}

	/** Save one or many taps. Same tap twice = one row. A bad tap gets its own error, the others are saved. */
	@PostMapping("/marks")
	@PreAuthorize("hasAnyAuthority('TRIPS_RECORD', 'TRIPS_RECORD_ANY')")
	public MarksResponse marks(@Valid @RequestBody MarksRequest request) {
		return new MarksResponse(markService.apply(currentUser.id(), request.marks()));
	}

}
