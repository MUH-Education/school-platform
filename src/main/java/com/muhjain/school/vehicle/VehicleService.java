package com.muhjain.school.vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vehicles and their four papers. Rules 1 to 3 of docs/phases/phase-2-vehicles-staff-routes.md.
 * Other features use this service, never {@link VehicleRepository}.
 * Example: Van 4, HR 23 A 1104, 14 seats, insurance valid till 28 Oct 2026.
 */
@Service
public class VehicleService {

	private final VehicleRepository vehicles;

	private final VehicleDocumentRepository documents;

	private final Clock clock;

	public VehicleService(VehicleRepository vehicles, VehicleDocumentRepository documents, Clock clock) {
		this.vehicles = vehicles;
		this.documents = documents;
		this.clock = clock;
	}

	/** All vehicles, turned off ones too, oldest first. */
	@Transactional(readOnly = true)
	public List<VehicleResponse> list() {
		List<Vehicle> all = vehicles.findAllByOrderByIdAsc();
		Map<Long, List<VehicleDocument>> papers = documents
			.findByVehicleIdIn(all.stream().map(Vehicle::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(VehicleDocument::getVehicleId));
		LocalDate today = today();
		return all.stream().map(v -> toResponse(v, papers.getOrDefault(v.getId(), List.of()), today)).toList();
	}

	/** @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public VehicleResponse get(Long id) {
		return toResponse(find(id), documents.findByVehicleId(id), today());
	}

	/**
	 * Rule 1. Example: "Van 4" with "HR 23 A 1104". A second "van4" or "hr23a1104" is refused.
	 *
	 * @throws ApiException 409 VEHICLE_NAME_ALREADY_USED, 409 REGISTRATION_ALREADY_USED
	 */
	@Transactional
	public VehicleResponse create(CreateVehicleRequest request) {
		String name = NameKeys.tidy(request.name());
		String registrationNo = NameKeys.tidy(request.registrationNo());
		checkUnique(name, registrationNo, 0L);
		Vehicle vehicle = new Vehicle(name, registrationNo, request.vehicleType(), request.seats(),
				request.monthlyCost().setScale(2), request.ownedBy());
		vehicle = save(vehicle);
		return toResponse(vehicle, List.of(), today());
	}

	/**
	 * Change details. Turning a vehicle off or on goes through here too ({@code active}).
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 VEHICLE_NAME_ALREADY_USED, 409 REGISTRATION_ALREADY_USED
	 */
	@Transactional
	public VehicleResponse update(Long id, UpdateVehicleRequest request) {
		Vehicle vehicle = find(id);
		String name = NameKeys.tidy(request.name());
		String registrationNo = NameKeys.tidy(request.registrationNo());
		checkUnique(name, registrationNo, id);
		vehicle.setName(name);
		vehicle.setRegistrationNo(registrationNo);
		vehicle.setVehicleType(request.vehicleType());
		vehicle.setSeats(request.seats());
		vehicle.setMonthlyCost(request.monthlyCost().setScale(2));
		vehicle.setOwnedBy(request.ownedBy());
		vehicle.setActive(request.active());
		vehicle = save(vehicle);
		return toResponse(vehicle, documents.findByVehicleId(id), today());
	}

	/** Turn off. Vehicles are never deleted (B10). Turning off a vehicle that is already off changes nothing. */
	@Transactional
	public void turnOff(Long id) {
		Vehicle vehicle = find(id);
		vehicle.setActive(false);
	}

	/**
	 * Saves the four paper dates. A date that is null removes the paper.
	 * Example: insurance 28 Oct 2026, the others null → one row left in {@code vehicle_document}.
	 */
	@Transactional
	public VehicleResponse saveDocuments(Long id, VehicleDocumentsRequest request) {
		Vehicle vehicle = find(id);
		Map<DocType, VehicleDocument> existing = new EnumMap<>(DocType.class);
		documents.findByVehicleId(id).forEach(d -> existing.put(d.getDocType(), d));
		List<VehicleDocument> now = new ArrayList<>();
		for (DocType type : DocType.values()) {
			LocalDate date = request.dateOf(type);
			VehicleDocument row = existing.get(type);
			if (date == null && row != null) {
				documents.delete(row);
			}
			else if (date != null && row == null) {
				now.add(documents.save(new VehicleDocument(id, type, date)));
			}
			else if (date != null) {
				row.setValidTill(date);
				now.add(row);
			}
		}
		documents.flush();
		return toResponse(vehicle, now, today());
	}

	/** The school day, in the school zone. */
	LocalDate today() {
		return LocalDate.now(clock);
	}

	private Vehicle find(Long id) {
		return vehicles.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This vehicle does not exist."));
	}

	private void checkUnique(String name, String registrationNo, Long exceptId) {
		if (vehicles.existsByNameKey(NameKeys.key(name), exceptId)) {
			throw nameUsed();
		}
		if (vehicles.existsByRegistrationKey(NameKeys.key(registrationNo), exceptId)) {
			throw registrationUsed();
		}
	}

	// The checks above can miss a vehicle saved at the same moment. The unique indexes still catch it.
	private Vehicle save(Vehicle vehicle) {
		try {
			return vehicles.saveAndFlush(vehicle);
		}
		catch (DataIntegrityViolationException ex) {
			String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
			if (cause.contains("vehicle_registration_no_uk")) {
				throw registrationUsed();
			}
			if (cause.contains("vehicle_name_uk")) {
				throw nameUsed();
			}
			throw ex;
		}
	}

	private static ApiException nameUsed() {
		return new ApiException(HttpStatus.CONFLICT, "VEHICLE_NAME_ALREADY_USED",
				"Another vehicle already has this name.");
	}

	private static ApiException registrationUsed() {
		return new ApiException(HttpStatus.CONFLICT, "REGISTRATION_ALREADY_USED",
				"Another vehicle already has this registration number.");
	}

	// Always the four papers, in the same order. A paper with no row is MISSING.
	private static VehicleResponse toResponse(Vehicle vehicle, List<VehicleDocument> papers, LocalDate today) {
		Map<DocType, LocalDate> dates = new EnumMap<>(DocType.class);
		papers.forEach(d -> dates.put(d.getDocType(), d.getValidTill()));
		List<DocumentResponse> all = new ArrayList<>();
		for (DocType type : DocType.values()) {
			all.add(DocumentResponse.of(type, dates.get(type), today));
		}
		return VehicleResponse.of(vehicle, all);
	}

}
