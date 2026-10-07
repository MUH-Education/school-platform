package com.muhjain.school.vehicle;

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
 * The Vehicles screens. Reading needs VEHICLES_VIEW, changing needs VEHICLES_EDIT.
 * Vehicles are never deleted: DELETE turns one off.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

	private final VehicleService vehicleService;

	public VehicleController(VehicleService vehicleService) {
		this.vehicleService = vehicleService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public List<VehicleResponse> list() {
		return vehicleService.list();
	}

	@PostMapping
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest request) {
		return vehicleService.create(request);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
	public VehicleResponse get(@PathVariable Long id) {
		return vehicleService.get(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateVehicleRequest request) {
		return vehicleService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void turnOff(@PathVariable Long id) {
		vehicleService.turnOff(id);
	}

	@PutMapping("/{id}/documents")
	@PreAuthorize("hasAuthority('VEHICLES_EDIT')")
	public VehicleResponse saveDocuments(@PathVariable Long id, @RequestBody VehicleDocumentsRequest request) {
		return vehicleService.saveDocuments(id, request);
	}

}
