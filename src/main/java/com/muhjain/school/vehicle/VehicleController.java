package com.muhjain.school.vehicle;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Vehicles screens. Reading needs VEHICLES_VIEW, changing needs VEHICLES_EDIT.
 * Vehicles are never deleted: DELETE turns one off.
 */
@Tag(name = "Vehicles")
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

	private final VehicleOverviewService vehicleService;

	public VehicleController(VehicleOverviewService vehicleService) {
		this.vehicleService = vehicleService;
	}

	/** {@code ?date=2026-10-14} shows the people of that day. Without it, today. */
	@Operation(summary = "List vehicles")
	@GetMapping
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public List<VehicleResponse> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return vehicleService.list(date);
	}

	/** Papers and licences that ended or end within 30 days. */
	@Operation(summary = "Documents that end soon")
	@GetMapping("/attention")
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public List<AttentionItem> attention() {
		return vehicleService.attention();
	}

	@Operation(summary = "Add a vehicle")
	@PostMapping
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest request) {
		return vehicleService.create(request);
	}

	@Operation(summary = "Show one vehicle")
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public VehicleResponse get(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return vehicleService.get(id, date);
	}

	@Operation(summary = "Change a vehicle")
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateVehicleRequest request) {
		return vehicleService.update(id, request);
	}

	@Operation(summary = "Turn a vehicle off")
	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void turnOff(@PathVariable Long id) {
		vehicleService.turnOff(id);
	}

	@Operation(summary = "Save the documents of a vehicle")
	@PutMapping("/{id}/documents")
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	public VehicleResponse saveDocuments(@PathVariable Long id, @RequestBody VehicleDocumentsRequest request) {
		return vehicleService.saveDocuments(id, request);
	}

}
