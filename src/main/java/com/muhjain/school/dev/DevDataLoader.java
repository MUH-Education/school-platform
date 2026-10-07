package com.muhjain.school.dev;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.muhjain.school.route.CreateRouteRequest;
import com.muhjain.school.route.RouteResponse;
import com.muhjain.school.route.RouteService;
import com.muhjain.school.route.StopRequest;
import com.muhjain.school.staff.AssignmentService;
import com.muhjain.school.staff.ChangeAssignmentRequest;
import com.muhjain.school.staff.CreateStaffRequest;
import com.muhjain.school.staff.Duty;
import com.muhjain.school.staff.StaffResponse;
import com.muhjain.school.staff.StaffService;
import com.muhjain.school.staff.StaffType;
import com.muhjain.school.vehicle.CreateVehicleRequest;
import com.muhjain.school.vehicle.OwnedBy;
import com.muhjain.school.vehicle.VehicleDocumentsRequest;
import com.muhjain.school.vehicle.VehicleResponse;
import com.muhjain.school.vehicle.VehicleService;
import com.muhjain.school.vehicle.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty development database with the school's fleet, so the screens have data while you build.
 * <ul>
 * <li>9 vehicles: Van 1 to Van 7 (14 seats) and Bus 8, Bus 9 (26 seats), 30,300 a month each. 150 seats in all.</li>
 * <li>9 routes: Route 1 to Route 9, Route n runs vehicle n, with ordered stops.</li>
 * <li>A driver and an attendant on every vehicle, from the start of the school year. Plus a spare driver
 * (Surender) and a spare attendant (Naresh), so you can try a leave: "Surender drives Van 4, 12 to 16 Oct".</li>
 * <li>Papers: all valid, except Van 2 insurance (ends in 12 days) and Van 6 PUC (ended 5 days ago).
 * The licence of Dalbir (Van 7) ends in 20 days. So the "needs attention" list is not empty.</li>
 * </ul>
 * <b>Only the {@code dev} profile.</b> It never runs in {@code test} or {@code prod}.
 * It does nothing if there is already a vehicle. Phone numbers are made up. It uses services only.
 */
@Component
@Profile("dev")
public class DevDataLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);

	private static final BigDecimal MONTHLY_COST = new BigDecimal("30300.00");

	private static final String[] DRIVERS = { "Rajpal", "Sunil", "Mahender", "Jagdish", "Ramphal", "Satbir", "Dalbir",
			"Karan", "Virender" };

	private static final String[] ATTENDANTS = { "Kamla", "Sunita", "Bimla", "Balwan", "Santosh", "Rekha", "Pushpa",
			"Mahavir", "Anil" };

	// Stops of Route 1 to Route 9. The school is in Tohana, so every route ends there.
	private static final String[][] STOPS = {
			{ "Dhamtan", "Kirdhan", "Barsola", "Tohana town" },
			{ "Hasangarh", "Ahlisadar", "Nagpur", "Tohana town" },
			{ "Bhuna road", "Pirthala", "Dhani", "Tohana town" },
			{ "Sadhanwas", "Jakhal", "Kanheri", "Tohana town" },
			{ "Ratia", "Jandli", "Chandrawal", "Tohana town" },
			{ "Bhattu", "Dhand", "Mehuwala", "Tohana town" },
			{ "Fatehabad road", "Ginnar", "Jamalpur", "Tohana town" },
			{ "Kulan", "Dhangar", "Sirsa road", "Rampura", "Tohana town" },
			{ "Model Town", "Sector 1", "Hisar road", "Bhodia Khera", "Tohana town" } };

	private final VehicleService vehicleService;

	private final StaffService staffService;

	private final AssignmentService assignmentService;

	private final RouteService routeService;

	private final Clock clock;

	public DevDataLoader(VehicleService vehicleService, StaffService staffService,
			AssignmentService assignmentService, RouteService routeService, Clock clock) {
		this.vehicleService = vehicleService;
		this.staffService = staffService;
		this.assignmentService = assignmentService;
		this.routeService = routeService;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!vehicleService.list().isEmpty()) {
			log.info("Dev data: there are vehicles already, nothing loaded");
			return;
		}
		LocalDate today = LocalDate.now(clock);
		LocalDate yearStart = schoolYearStart(today);

		int phoneCounter = 0;
		List<Long> drivers = new ArrayList<>();
		List<Long> attendants = new ArrayList<>();
		for (int i = 0; i < DRIVERS.length; i++) {
			// Dalbir (Van 7) has a licence that ends in 20 days. The others are valid for years.
			LocalDate licenceTill = (DRIVERS[i].equals("Dalbir")) ? today.plusDays(20) : today.plusYears(3 + i % 3);
			drivers.add(addStaff(DRIVERS[i], StaffType.DRIVER, ++phoneCounter, "HR26" + (2015 + i) + "0012" + (300 + i),
					licenceTill));
			attendants.add(addStaff(ATTENDANTS[i], StaffType.ATTENDANT, ++phoneCounter, null, null));
		}
		addStaff("Surender", StaffType.DRIVER, ++phoneCounter, "HR262016001299", today.plusYears(4));
		addStaff("Naresh", StaffType.ATTENDANT, ++phoneCounter, null, null);

		for (int n = 1; n <= 9; n++) {
			boolean bus = n >= 8;
			VehicleResponse vehicle = vehicleService.create(new CreateVehicleRequest((bus ? "Bus " : "Van ") + n,
					"HR 23 " + (bus ? "B" : "A") + " 110" + n, bus ? VehicleType.MID_BUS : VehicleType.SMALL_VAN,
					bus ? 26 : 14, MONTHLY_COST, OwnedBy.CONTRACTOR));
			vehicleService.saveDocuments(vehicle.id(), papers(n, today));
			assign(vehicle.id(), Duty.DRIVER, drivers.get(n - 1), yearStart);
			assign(vehicle.id(), Duty.ATTENDANT, attendants.get(n - 1), yearStart);

			RouteResponse route = routeService.create(new CreateRouteRequest("Route " + n, vehicle.id()));
			routeService.saveStops(route.id(), stops(n));
		}
		log.info("Dev data loaded: 9 vehicles, 9 routes, {} staff", DRIVERS.length * 2 + 2);
	}

	private Long addStaff(String name, StaffType type, int phoneNumber, String licenceNo, LocalDate licenceTill) {
		// Made-up numbers: 9876543201, 9876543202, ...
		StaffResponse person = staffService.create(new CreateStaffRequest(name, "98765432" + String.format("%02d",
				phoneNumber), type, licenceNo, licenceTill));
		return person.id();
	}

	private void assign(Long vehicleId, Duty duty, Long staffId, LocalDate from) {
		assignmentService.change(vehicleId, new ChangeAssignmentRequest(duty, staffId, from, null, false, null), null);
	}

	// All papers valid for months. Two exceptions, to fill the "needs attention" list.
	private static VehicleDocumentsRequest papers(int vehicle, LocalDate today) {
		LocalDate insurance = (vehicle == 2) ? today.plusDays(12) : today.plusMonths(8);
		LocalDate puc = (vehicle == 6) ? today.minusDays(5) : today.plusMonths(5);
		return new VehicleDocumentsRequest(today.plusMonths(10), insurance, today.plusYears(1), puc);
	}

	// The first stop is at 07:10. Every next stop is 12 minutes later.
	private static List<StopRequest> stops(int route) {
		List<StopRequest> stops = new ArrayList<>();
		LocalTime time = LocalTime.of(7, 10);
		for (String name : STOPS[route - 1]) {
			stops.add(new StopRequest(null, name, time, null));
			time = time.plusMinutes(12);
		}
		return stops;
	}

	// 1 April is the start of the school year.
	private static LocalDate schoolYearStart(LocalDate today) {
		return LocalDate.of((today.getMonthValue() >= 4) ? today.getYear() : today.getYear() - 1, 4, 1);
	}

}
