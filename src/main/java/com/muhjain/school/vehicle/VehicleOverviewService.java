package com.muhjain.school.vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.muhjain.school.staff.AssignmentService;
import com.muhjain.school.staff.Crew;
import com.muhjain.school.staff.StaffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the Vehicles screens show: a vehicle together with the people on it. The controller calls only this class.
 * <p>
 * Why not put this in {@link VehicleService}? People and routes need the vehicle (they call VehicleService).
 * If VehicleService also called them, two services would need each other. So VehicleService is the low layer
 * and this class sits on top of it.
 */
@Service
public class VehicleOverviewService {

	private final VehicleService vehicleService;

	private final AssignmentService assignmentService;

	private final StaffService staffService;

	private final Clock clock;

	public VehicleOverviewService(VehicleService vehicleService, AssignmentService assignmentService,
			StaffService staffService, Clock clock) {
		this.vehicleService = vehicleService;
		this.assignmentService = assignmentService;
		this.staffService = staffService;
		this.clock = clock;
	}

	/**
	 * All vehicles with the people of one day.
	 *
	 * @param date the day, or null for today. Example: 14 Oct shows Surender as driver of Van 4.
	 */
	@Transactional(readOnly = true)
	public List<VehicleResponse> list(LocalDate date) {
		LocalDate day = dayOrToday(date);
		List<VehicleResponse> vehicles = vehicleService.list();
		Map<Long, Crew> crews = assignmentService.onDate(vehicles.stream().map(VehicleResponse::id).toList(), day);
		return vehicles.stream().map(v -> v.withCrew(crews.get(v.id()))).toList();
	}

	/** @throws com.muhjain.school.common.ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public VehicleResponse get(Long id, LocalDate date) {
		return withCrew(vehicleService.get(id), dayOrToday(date));
	}

	/**
	 * Papers of vehicles and licences of drivers that have ended or end within 30 days. The most urgent first.
	 * Example on 7 Oct: Van 4 insurance valid till 17 Oct → ENDING_SOON, 10 days left.
	 */
	@Transactional(readOnly = true)
	public List<AttentionItem> attention() {
		LocalDate today = today();
		return Stream.concat(vehicleService.paperAttention(today).stream(),
				staffService.licenceAttention(today).stream())
			.sorted(Comparator.comparing(AttentionItem::validTill).thenComparing(AttentionItem::kind))
			.toList();
	}

	@Transactional
	public VehicleResponse create(CreateVehicleRequest request) {
		return withCrew(vehicleService.create(request), today());
	}

	@Transactional
	public VehicleResponse update(Long id, UpdateVehicleRequest request) {
		return withCrew(vehicleService.update(id, request), today());
	}

	@Transactional
	public void turnOff(Long id) {
		vehicleService.turnOff(id);
	}

	@Transactional
	public VehicleResponse saveDocuments(Long id, VehicleDocumentsRequest request) {
		return withCrew(vehicleService.saveDocuments(id, request), today());
	}

	private VehicleResponse withCrew(VehicleResponse vehicle, LocalDate day) {
		return vehicle.withCrew(assignmentService.onDate(vehicle.id(), day));
	}

	private LocalDate dayOrToday(LocalDate date) {
		return (date != null) ? date : today();
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

}
