package com.muhjain.school.trip;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The office screens "Bus status" and "One bus". Needs BUS_STATUS_VIEW. The web app asks again every 30 seconds.
 */
@Tag(name = "Bus status")
@RestController
@RequestMapping("/api/v1/bus-status")
public class BusStatusController {

	private final BusStatusService busStatusService;

	private final AttentionService attentionService;

	public BusStatusController(BusStatusService busStatusService, AttentionService attentionService) {
		this.busStatusService = busStatusService;
		this.attentionService = attentionService;
	}

	/** Every route. {@code phase} default: MORNING before 12:00, EVENING after. {@code date} default: today. */
	@Operation(summary = "Status of every bus now")
	@GetMapping
	@PreAuthorize("hasAuthority('BUS_STATUS_VIEW')")
	public List<RouteStatusResponse> all(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) BusPhase phase) {
		return busStatusService.all(date, phase);
	}

	/** One route: its status and every child with the four events. */
	@Operation(summary = "Status of one bus with its children")
	@GetMapping("/routes/{routeId}")
	@PreAuthorize("hasAuthority('BUS_STATUS_VIEW')")
	public RouteDetailResponse route(@PathVariable Long routeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) BusPhase phase) {
		return busStatusService.route(routeId, date, phase);
	}

	/** Buses with no taps or late (morning), and children nobody answered for in the evening. */
	@Operation(summary = "Buses that need attention")
	@GetMapping("/attention")
	@PreAuthorize("hasAuthority('BUS_STATUS_VIEW')")
	public AttentionResponse attention(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return attentionService.attention(date);
	}

}
