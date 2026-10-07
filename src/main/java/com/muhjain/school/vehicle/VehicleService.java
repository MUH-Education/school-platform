package com.muhjain.school.vehicle;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditChanges;
import com.muhjain.school.audit.AuditService;
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

	static final String ENTITY = "VEHICLE";

	private final VehicleRepository vehicles;

	private final VehicleDocumentRepository documents;

	private final AuditService auditService;

	private final Clock clock;

	public VehicleService(VehicleRepository vehicles, VehicleDocumentRepository documents,
			AuditService auditService, Clock clock) {
		this.vehicles = vehicles;
		this.documents = documents;
		this.auditService = auditService;
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
		auditService.record(ENTITY, vehicle.getId(), AuditAction.CREATED,
				"Vehicle " + name + " (" + registrationNo + ") added", null);
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
		BigDecimal monthlyCost = request.monthlyCost().setScale(2);
		AuditChanges changes = new AuditChanges().field("Name", "name", vehicle.getName(), name)
			.field("Registration number", "registrationNo", vehicle.getRegistrationNo(), registrationNo)
			.field("Type", "vehicleType", vehicle.getVehicleType(), request.vehicleType())
			.field("Seats", "seats", vehicle.getSeats(), request.seats())
			.field("Monthly cost", "monthlyCost", vehicle.getMonthlyCost(), monthlyCost)
			.field("Owned by", "ownedBy", vehicle.getOwnedBy(), request.ownedBy())
			.active(vehicle.isActive(), request.active());
		vehicle.setName(name);
		vehicle.setRegistrationNo(registrationNo);
		vehicle.setVehicleType(request.vehicleType());
		vehicle.setSeats(request.seats());
		vehicle.setMonthlyCost(monthlyCost);
		vehicle.setOwnedBy(request.ownedBy());
		vehicle.setActive(request.active());
		vehicle = save(vehicle);
		if (!changes.isEmpty()) {
			auditService.record(ENTITY, id, AuditAction.UPDATED, changes.summary(), changes.details());
		}
		return toResponse(vehicle, documents.findByVehicleId(id), today());
	}

	/** Turn off. Vehicles are never deleted (B10). Turning off a vehicle that is already off changes nothing. */
	@Transactional
	public void turnOff(Long id) {
		Vehicle vehicle = find(id);
		if (vehicle.isActive()) {
			vehicle.setActive(false);
			AuditChanges changes = new AuditChanges().active(true, false);
			auditService.record(ENTITY, id, AuditAction.UPDATED, changes.summary(), changes.details());
		}
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
		AuditChanges changes = new AuditChanges();
		for (DocType type : DocType.values()) {
			LocalDate date = request.dateOf(type);
			VehicleDocument row = existing.get(type);
			changes.field(label(type) + " valid till", type.name().toLowerCase(Locale.ROOT),
					(row != null) ? row.getValidTill() : null, date);
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
		if (!changes.isEmpty()) {
			auditService.record(ENTITY, id, AuditAction.UPDATED, changes.summary(), changes.details());
		}
		return toResponse(vehicle, now, today());
	}

	/**
	 * Locks the vehicle row until the end of the transaction, so two changes of its people cannot run at the same
	 * moment. Call it inside a transaction.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 VEHICLE_INACTIVE
	 */
	@Transactional
	public VehicleResponse lockActive(Long id) {
		Vehicle vehicle = vehicles.findByIdForUpdate(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This vehicle does not exist."));
		if (!vehicle.isActive()) {
			throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_INACTIVE",
					vehicle.getName() + " is turned off. Turn it on first.");
		}
		return toResponse(vehicle, documents.findByVehicleId(id), today());
	}

	/**
	 * Papers of turned-on vehicles that have ended or end within 30 days, the most urgent first.
	 * Papers with no date are not listed (nothing to count).
	 */
	@Transactional(readOnly = true)
	public List<AttentionItem> paperAttention(LocalDate today) {
		List<VehicleDocument> due = documents
			.findByValidTillLessThanEqualOrderByValidTillAscIdAsc(today.plusDays(PaperStatus.SOON_DAYS));
		Map<Long, Vehicle> byId = vehicles.findAllById(due.stream().map(VehicleDocument::getVehicleId).toList())
			.stream()
			.filter(Vehicle::isActive)
			.collect(Collectors.toMap(Vehicle::getId, v -> v));
		return due.stream()
			.filter(d -> byId.containsKey(d.getVehicleId()))
			.map(d -> AttentionItem.paper(d.getVehicleId(), byId.get(d.getVehicleId()).getName(), d.getDocType(),
					d.getValidTill(), today))
			.toList();
	}

	/** @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public void requireExists(Long id) {
		find(id);
	}

	/** Short facts for other features. Ids that do not exist are left out. */
	@Transactional(readOnly = true)
	public Map<Long, VehicleSummary> summaries(Collection<Long> ids) {
		return vehicles.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(Vehicle::getId, VehicleSummary::of));
	}

	/** Every vehicle that is turned on, oldest first. The "whole fleet" of the load board. */
	@Transactional(readOnly = true)
	public List<VehicleSummary> activeSummaries() {
		return vehicles.findByActiveTrueOrderByIdAsc().stream().map(VehicleSummary::of).toList();
	}

	/** Names for other features. Example: {4 → "Van 4"}. Ids that do not exist are left out. */
	@Transactional(readOnly = true)
	public Map<Long, String> names(Collection<Long> ids) {
		return vehicles.findAllById(ids).stream().collect(Collectors.toMap(Vehicle::getId, Vehicle::getName));
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

	// "FITNESS" → "Fitness", "PUC" → "PUC". The short ones are written in capitals.
	private static String label(DocType type) {
		return (type == DocType.PUC) ? "PUC" : type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT);
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
