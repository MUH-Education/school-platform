package com.muhjain.school.service.dev;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.muhjain.school.service.auth.OtpService;
import com.muhjain.school.model.dto.request.EnquiryRequest;
import com.muhjain.school.model.dto.response.EnquiryResponse;
import com.muhjain.school.service.enquiry.EnquiryService;
import com.muhjain.school.model.enums.EnquirySource;
import com.muhjain.school.model.enums.EnquiryStatus;
import com.muhjain.school.model.dto.request.FollowUpRequest;
import com.muhjain.school.model.enums.NeedsBus;
import com.muhjain.school.model.dto.request.StatusRequest;
import com.muhjain.school.model.entity.AcademicSession;
import com.muhjain.school.model.dto.response.ClassFeeItem;
import com.muhjain.school.service.fee.ClassFeeService;
import com.muhjain.school.model.dto.request.ClassFeesRequest;
import com.muhjain.school.model.enums.DiscountReason;
import com.muhjain.school.model.dto.response.DueResponse;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.model.dto.request.FeePlanRequest;
import com.muhjain.school.model.dto.response.FeePlanResponse;
import com.muhjain.school.service.fee.FeePlanService;
import com.muhjain.school.model.enums.PayFrequency;
import com.muhjain.school.model.dto.response.PaymentLine;
import com.muhjain.school.model.enums.PaymentMode;
import com.muhjain.school.model.dto.request.PaymentRequest;
import com.muhjain.school.service.fee.PaymentService;
import com.muhjain.school.service.fee.SessionService;
import com.muhjain.school.model.dto.request.CreateRouteRequest;
import com.muhjain.school.model.dto.response.RouteResponse;
import com.muhjain.school.service.route.RouteService;
import com.muhjain.school.model.dto.request.StopRequest;
import com.muhjain.school.service.staff.AssignmentService;
import com.muhjain.school.model.dto.request.ChangeAssignmentRequest;
import com.muhjain.school.model.dto.request.CreateStaffRequest;
import com.muhjain.school.model.enums.Duty;
import com.muhjain.school.model.dto.response.StaffResponse;
import com.muhjain.school.service.staff.StaffService;
import com.muhjain.school.model.enums.StaffType;
import com.muhjain.school.model.dto.response.AdmissionBus;
import com.muhjain.school.model.dto.request.AdmissionRequest;
import com.muhjain.school.model.dto.response.AdmissionResponse;
import com.muhjain.school.service.student.AdmissionService;
import com.muhjain.school.util.ClassNames;
import com.muhjain.school.model.enums.FatherOccupation;
import com.muhjain.school.model.enums.Gender;
import com.muhjain.school.model.enums.GuardianRelation;
import com.muhjain.school.model.dto.request.GuardianRequest;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.model.dto.response.RouteChild;
import com.muhjain.school.model.dto.response.StudentFilter;
import com.muhjain.school.service.student.StudentQueryService;
import com.muhjain.school.service.student.StudentService;
import com.muhjain.school.model.enums.StudentStatus;
import com.muhjain.school.model.dto.request.UpdateStatusRequest;
import com.muhjain.school.model.enums.EventType;
import com.muhjain.school.model.dto.request.MarkRequest;
import com.muhjain.school.model.dto.response.MarkResult;
import com.muhjain.school.service.trip.MarkService;
import com.muhjain.school.model.enums.Outcome;
import com.muhjain.school.model.dto.request.CreateUserRequest;
import com.muhjain.school.model.enums.Permission;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.model.dto.response.UserResponse;
import com.muhjain.school.service.user.UserService;
import com.muhjain.school.model.dto.request.CreateVehicleRequest;
import com.muhjain.school.model.enums.OwnedBy;
import com.muhjain.school.model.dto.request.VehicleDocumentsRequest;
import com.muhjain.school.model.dto.response.VehicleResponse;
import com.muhjain.school.service.vehicle.VehicleService;
import com.muhjain.school.model.enums.VehicleType;
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
 * Phase 3 adds about 40 students across the routes:
 * <ul>
 * <li>28 families. 12 of them have two children (brother and sister, or two brothers) who <b>share one parent
 * phone</b>, so the guardian table has one row for that phone.</li>
 * <li>4 families have no bus. One child starts the bus 25 days from now (so {@code onRoute} does not list the child
 * before that day). One child has left school (status LEFT).</li>
 * <li>A grandfather's phone that already belongs to a cousin is added to one child, to show one guardian row for
 * one phone.</li>
 * </ul>
 * Phase 4 adds the taps of a morning, so Bus status looks like the design when you open it at 7:48 (each route
 * has its own stop times for this). Today, once a day:
 * <ul>
 * <li>Route 1, 6, 8: children tapped on time → ON_THE_WAY.</li>
 * <li>Route 2: all tapped, REACHED_SCHOOL at 07:46.</li>
 * <li>Route 3: no taps, first stop due 07:15 → NO_TAPS, 33 minutes.</li>
 * <li>Route 4: first stop tapped, the next one is 26 minutes overdue → LATE. Route 5: tapped 07:40, due 07:24 →
 * LATE, 16 minutes.</li>
 * <li>Route 7, 9: no taps yet, first stop due 07:40 and 07:50 → NOT_STARTED.</li>
 * </ul>
 * The taps go in through {@link MarkService}, as an office user (the first OWNER, OFFICE_ADMIN or
 * TRANSPORT_INCHARGE). Without such a user (set APP_OWNER_PHONE) the taps are skipped. A tap time more than 5
 * minutes ahead of the real clock is replaced by the real time, as for a phone, so start the app after 07:45 to
 * see the exact picture. Older databases keep their old stop times.
 * <p>
 * Also adds 4 test logins, one per role that can log in (OWNER, OFFICE_ADMIN, TRANSPORT_INCHARGE, the
 * ATTENDANT is Balwan from the fleet above). Each phone is whitelisted, so {@code otp/request} never sends a
 * real message for it and {@code otp/verify} always accepts the fixed code (see docs/08-decisions.md).
 * Phase 6 adds 29 enquiries for the next session: 7 NEW, 6 CONTACTED, 5 VISITED, 3 APPLIED, 4 ADMITTED (linked to
 * real students) and 4 LOST, 5 of them overdue (the CONTACTED ones with a past date). So the enquiry screens have numbers: 29 enquiries, 14% admitted.
 * Phase 7 adds the standard class fees and a fee plan for every active child (half on time, a quarter delayed, a
 * quarter defaulted), with the payments that make those states, when today is inside the current session.
 * The fleet, the students, the fees, the taps and the enquiries are loaded separately. Each part does nothing if its data is already there.
 * <b>Only the {@code dev} profile.</b> It never runs in {@code test} or {@code prod}.
 * Phone numbers are made up. It uses services only.
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

	private final AdmissionService admissionService;

	private final GuardianService guardianService;

	private final StudentService studentService;

	private final StudentQueryService studentQuery;

	private final MarkService markService;

	private final UserService userService;

	private final OtpService otpService;

	private final EnquiryService enquiryService;

	private final SessionService sessionService;

	private final ClassFeeService classFeeService;

	private final FeePlanService feePlanService;

	private final PaymentService paymentService;

	private final Clock clock;

	public DevDataLoader(VehicleService vehicleService, StaffService staffService,
			AssignmentService assignmentService, RouteService routeService, AdmissionService admissionService,
			GuardianService guardianService, StudentService studentService, StudentQueryService studentQuery,
			MarkService markService, UserService userService, OtpService otpService, EnquiryService enquiryService,
			SessionService sessionService, ClassFeeService classFeeService, FeePlanService feePlanService,
			PaymentService paymentService, Clock clock) {
		this.sessionService = sessionService;
		this.classFeeService = classFeeService;
		this.feePlanService = feePlanService;
		this.paymentService = paymentService;
		this.enquiryService = enquiryService;
		this.studentQuery = studentQuery;
		this.markService = markService;
		this.userService = userService;
		this.otpService = otpService;
		this.admissionService = admissionService;
		this.guardianService = guardianService;
		this.studentService = studentService;
		this.vehicleService = vehicleService;
		this.staffService = staffService;
		this.assignmentService = assignmentService;
		this.routeService = routeService;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (vehicleService.list().isEmpty()) {
			loadFleet();
		}
		else {
			log.info("Dev data: there are vehicles already, fleet not loaded");
		}
		loadTestLogins();
		if (studentService.list(new StudentFilter(null, null, null, null, null, null), 0, 1, null)
			.totalItems() == 0) {
			loadStudents();
		}
		else {
			log.info("Dev data: there are students already, students not loaded");
		}
		loadFees();
		loadTaps();
		if (enquiryService.summary().total() == 0) {
			loadEnquiries();
		}
		else {
			log.info("Dev data: there are enquiries already, enquiries not loaded");
		}
	}

	private void loadFleet() {
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

			LocalTime schoolDueMorning = lastStopTime(n).plusMinutes(10);
			RouteResponse route = routeService
				.create(new CreateRouteRequest("Route " + n, vehicle.id(), schoolDueMorning, null));
			routeService.saveStops(route.id(), stops(n));
		}
		log.info("Dev data loaded: 9 vehicles, 9 routes, {} staff", DRIVERS.length * 2 + 2);
	}

	// ---- test logins (one per role, fixed OTP) ----

	// Made up, in no other dev data range, so they are obviously test numbers.
	private static final String TEST_OWNER_PHONE = "9999900001";

	private static final String TEST_OFFICE_ADMIN_PHONE = "9999900002";

	private static final String TEST_TRANSPORT_INCHARGE_PHONE = "9999900003";

	private static final String TEST_ATTENDANT_PHONE = "9999900004";

	// One login per role that can log in. The attendant reuses Balwan (Route 4) from the fleet above,
	// so docs/05-roles-permissions.md's Balwan examples work against a real test login too.
	private void loadTestLogins() {
		if (userService.list().stream().anyMatch(u -> u.phone().endsWith(TEST_OWNER_PHONE))) {
			log.info("Dev data: test logins already there, not loaded");
			return;
		}
		userService.create(new CreateUserRequest(TEST_OWNER_PHONE, Role.OWNER, "Test owner", null), null);
		userService.create(new CreateUserRequest(TEST_OFFICE_ADMIN_PHONE, Role.OFFICE_ADMIN, "Test office admin",
				null), null);
		userService.create(new CreateUserRequest(TEST_TRANSPORT_INCHARGE_PHONE, Role.TRANSPORT_INCHARGE,
				"Test transport in-charge", null), null);
		Long balwanId = staffService.list(Set.of(StaffType.ATTENDANT))
			.stream()
			.filter(s -> "Balwan".equals(s.name()))
			.map(StaffResponse::id)
			.findFirst()
			.orElse(null);
		if (balwanId != null) {
			userService.create(new CreateUserRequest(TEST_ATTENDANT_PHONE, Role.ATTENDANT,
					"Test attendant (Balwan)", balwanId), null);
		}
		for (String phone : List.of(TEST_OWNER_PHONE, TEST_OFFICE_ADMIN_PHONE, TEST_TRANSPORT_INCHARGE_PHONE,
				TEST_ATTENDANT_PHONE)) {
			otpService.addToWhitelist(phone, "Dev test login");
		}
		log.info("Dev data loaded: 4 test logins (owner, office admin, transport in-charge, attendant); "
				+ "fixed OTP is 123456 (1234 if app.otp.length is 4)");
	}

	// ---- students (Phase 3) ----

	private static final String[] BOYS = { "Aryan", "Ishaan", "Vihaan", "Rohan", "Kabir", "Dev", "Harsh", "Lakshay",
			"Mohit", "Naman", "Parth", "Rudra", "Yash", "Tanmay", "Nitin", "Sahil", "Aman", "Gaurav" };

	private static final String[] GIRLS = { "Siya", "Ananya", "Diya", "Kavya", "Muskan", "Pooja", "Riya", "Sneha",
			"Tanvi", "Vani", "Khushi", "Palak" };

	private static final String[] FATHERS = { "Ramesh", "Suresh", "Mahipal", "Dharampal", "Jagbir", "Rajender", "Satyawan",
			"Balbir", "Naresh", "Subhash", "Krishan", "Om Prakash", "Ravinder", "Sandeep" };

	private static final String[] SURNAMES = { "Jain", "Sharma", "Goyal", "Bishnoi", "Jat", "Garg", "Singh",
			"Kumar" };

	private static final int FAMILIES = 28;

	private void loadStudents() {
		List<RouteResponse> routes = routeService.list();
		if (routes.isEmpty()) {
			log.info("Dev data: there are no routes, students not loaded");
			return;
		}
		LocalDate today = LocalDate.now(clock);
		LocalDate yearStart = schoolYearStart(today);
		FatherOccupation[] occupations = FatherOccupation.values();

		int boy = 0;
		int girl = 0;
		int children = 0;
		List<Long> firstChildOfFamily = new ArrayList<>();
		List<String> phones = new ArrayList<>();
		Long lastStudent = null;
		for (int f = 0; f < FAMILIES; f++) {
			String surname = SURNAMES[f % SURNAMES.length];
			String parent = FATHERS[f % FATHERS.length] + " " + surname;
			// Made-up numbers: 98111 00001, 98111 00002, ...
			String phone = "98111" + String.format("%05d", f + 1);
			phones.add(phone);
			RouteResponse route = routes.get(f % routes.size());
			// The first three stops are villages. The fourth is the town where the school is.
			int stopIndex = f % Math.min(3, route.stops().size());
			boolean busFamily = f % 7 != 6;
			// 12 families have two children and one shared phone.
			int kids = (f < 12) ? 2 : 1;
			for (int k = 0; k < kids; k++) {
				boolean isGirl = (k == 1 && f % 2 == 0) || (k == 0 && f % 5 == 4);
				String first = isGirl ? GIRLS[girl++ % GIRLS.length] : BOYS[boy++ % BOYS.length];
				String className = ClassNames.ALL.get((f + 5 * k) % ClassNames.ALL.size());
				int age = 3 + ClassNames.ALL.indexOf(className);
				LocalDate dob = LocalDate.of(today.getYear() - age, 1 + (children % 12), 1 + (children % 27));
				String village = busFamily ? route.stops().get(stopIndex).name() : "Tohana town";

				AdmissionBus bus = null;
				if (busFamily) {
					// Family 5: the first child starts the bus 25 days from now.
					LocalDate from = (f == 5 && k == 0) ? today.plusDays(25) : null;
					bus = new AdmissionBus(route.id(), route.stops().get(stopIndex).id(), from, null);
				}
				AdmissionResponse admitted = admissionService.admit(new AdmissionRequest(first + " " + surname,
						dob, isGirl ? Gender.F : Gender.M, className, (children % 2 == 0) ? "A" : "B", village, null,
						occupations[f % occupations.length], yearStart,
						List.of(new GuardianRequest(parent, phone, GuardianRelation.FATHER, true)), null, null, bus, null, null),
						null);
				if (k == 0) {
					firstChildOfFamily.add(admitted.studentId());
				}
				lastStudent = admitted.studentId();
				children++;
			}
		}
		// Family 3's first child gets the phone of family 4's father too, as "grandfather". It is the same number as a
		// cousin's parent, so there is still one guardian row for it.
		guardianService.add(firstChildOfFamily.get(3),
				new GuardianRequest("Dharampal Goyal", phones.get(4), GuardianRelation.GRANDFATHER, true));
		// The last child has left school 10 days ago.
		studentService.setStatus(lastStudent, new UpdateStatusRequest(StudentStatus.LEFT, today.minusDays(10)));
		log.info("Dev data loaded: {} students in {} families", children, FAMILIES);
	}

	private Long addStaff(String name, StaffType type, int phoneNumber, String licenceNo, LocalDate licenceTill) {
		// Made-up numbers: 9876543201, 9876543202, ...
		StaffResponse person = staffService.create(new CreateStaffRequest(name, "98765432" + String.format("%02d",
				phoneNumber), type, licenceNo, licenceTill, null, null));
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

	// Morning times of the stops of Route 1 to 9. Chosen so that at 07:48 the routes show every state (see above).
	// Route 4 keeps the 07:10 start. Example: Route 5 stops are due 07:12, 07:24, 07:36, 07:48.
	private static final String[][] STOP_TIMES = {
			{ "07:30", "07:42", "07:52", "08:00" },
			{ "07:10", "07:22", "07:34", "07:46" },
			{ "07:15", "07:27", "07:39", "07:51" },
			{ "07:10", "07:22", "07:34", "07:46" },
			{ "07:12", "07:24", "07:36", "07:48" },
			{ "07:20", "07:30", "07:40", "07:55" },
			{ "07:40", "07:48", "07:54", "07:58" },
			{ "07:20", "07:32", "07:42", "07:50", "07:58" },
			{ "07:50", "07:52", "07:54", "07:56", "07:58" } };

	// The route's last stop ("Tohana town") is closest to school. Due at school is a few minutes after it.
	private static LocalTime lastStopTime(int route) {
		String[] times = STOP_TIMES[route - 1];
		return LocalTime.parse(times[times.length - 1]);
	}

	private static List<StopRequest> stops(int route) {
		List<StopRequest> stops = new ArrayList<>();
		String[] names = STOPS[route - 1];
		for (int i = 0; i < names.length; i++) {
			stops.add(new StopRequest(null, names[i], LocalTime.parse(STOP_TIMES[route - 1][i]), null));
		}
		return stops;
	}

	// ---- enquiries (Phase 6) ----

	private static final String[] PARENTS = { "Ramesh Jain", "Suresh Goyal", "Mahipal Singh", "Dharampal Garg",
			"Jagbir Jat", "Rajender Bishnoi", "Satyawan Sharma", "Sunita Devi", "Kamla Rani", "Anil Kumar",
			"Vijay Mehta", "Pawan Jain", "Naresh Goyal", "Dalip Singh", "Balwan Jat", "Rekha Devi", "Manoj Garg",
			"Sanjay Bishnoi", "Pooja Rani", "Mukesh Sharma", "Ashok Kumar", "Neelam Devi", "Sandeep Jain",
			"Rakesh Goyal", "Vinod Singh", "Surender Jat", "Hari Om Garg", "Babita Devi", "Deepak Sharma" };

	private static final String[] VILLAGES = { "Jakhal", "Kanheri", "Sadhanwas", "Dhand", "Ratia", "Tohana town",
			"Bhattu", "Model Town" };

	private static final String[] CLASSES = { "Nursery", "LKG", "UKG", "1", "2", "3", "4", "5", "6", "7", "8", "9" };

	private static final EnquirySource[] SOURCES = { EnquirySource.WALK_IN, EnquirySource.REFERRAL,
			EnquirySource.FACEBOOK, EnquirySource.WHATSAPP, EnquirySource.HOARDING, EnquirySource.BUS_ENQUIRY };

	// Stage of enquiry number 0 to 28: 7 NEW, 6 CONTACTED, 5 VISITED, 3 APPLIED, 4 ADMITTED, 4 LOST.
	private static final EnquiryStatus[] STAGES = { EnquiryStatus.NEW, EnquiryStatus.NEW, EnquiryStatus.NEW,
			EnquiryStatus.NEW, EnquiryStatus.NEW, EnquiryStatus.NEW, EnquiryStatus.NEW, EnquiryStatus.CONTACTED,
			EnquiryStatus.CONTACTED, EnquiryStatus.CONTACTED, EnquiryStatus.CONTACTED, EnquiryStatus.CONTACTED,
			EnquiryStatus.CONTACTED, EnquiryStatus.VISITED, EnquiryStatus.VISITED, EnquiryStatus.VISITED,
			EnquiryStatus.VISITED, EnquiryStatus.VISITED, EnquiryStatus.APPLIED, EnquiryStatus.APPLIED,
			EnquiryStatus.APPLIED, EnquiryStatus.ADMITTED, EnquiryStatus.ADMITTED, EnquiryStatus.ADMITTED,
			EnquiryStatus.ADMITTED, EnquiryStatus.LOST, EnquiryStatus.LOST, EnquiryStatus.LOST,
			EnquiryStatus.LOST };

	/**
	 * Phase 7: the standard class fees and a fee plan for every active child, with a mix of on time (half), delayed
	 * and defaulted (a quarter each). Does nothing if a plan exists. Needs students, and today inside the current session.
	 * <p>
	 * How the mix is made: a DELAYED child pays a MONTHLY plan up to, but not including, the oldest due that is 11 to
	 * 60 days late. A DEFAULTED child pays up to, but not including, the oldest due that is more than 60 days late.
	 * An ON_TIME child pays every due up to today. Example on 7 Oct: DELAYED leaves the 1 Sep due unpaid (36 days).
	 */
	private void loadFees() {
		if (feePlanService.anyPlanExists()) {
			log.info("Dev data: there are fee plans already, fees not loaded");
			return;
		}
		AcademicSession session = sessionService.current();
		LocalDate today = LocalDate.now(clock);
		if (today.isBefore(session.getStartsOn()) || today.isAfter(session.getEndsOn())) {
			log.info("Dev data: today is outside the current session {}, fees not loaded", session.getName());
			return;
		}
		if (classFeeService.list(session.getId()).isEmpty()) {
			List<ClassFeeItem> fees = new ArrayList<>();
			for (int i = 0; i < ClassNames.ALL.size(); i++) {
				fees.add(new ClassFeeItem(ClassNames.ALL.get(i), BigDecimal.valueOf(18000 + 1500L * i)));
			}
			classFeeService.replace(session.getId(), new ClassFeesRequest(fees));
		}
		var children = studentService.list(new StudentFilter(null, null, null, null, null, null), 0, 100, "name")
			.items();
		PaymentMode[] modes = PaymentMode.values();
		int index = 0;
		for (var child : children) {
			String wanted = switch (index % 4) {
				case 2 -> "DELAYED";
				case 3 -> "DEFAULTED";
				default -> "ON_TIME";
			};
			PayFrequency frequency = switch (wanted) {
				case "DELAYED" -> PayFrequency.MONTHLY;
				case "DEFAULTED" -> (index % 8 == 3) ? PayFrequency.QUARTERLY : PayFrequency.YEARLY;
				default -> PayFrequency.values()[(index / 4) % 3];
			};
			boolean discount = index % 8 == 5;
			BigDecimal schoolFee = classFeeService.standardFee(session.getId(), child.className())
				.orElse(BigDecimal.valueOf(30000));
			FeePlanResponse plan = feePlanService.save(child.id(),
					new FeePlanRequest(schoolFee, (child.busNow() != null) ? new BigDecimal("8800") : null,
							discount ? new BigDecimal("2000") : null,
							discount ? DiscountReason.SIBLING : null, frequency),
					null);
			payUpTo(child.id(), plan, wanted, today, modes[index % modes.length]);
			index++;
		}
		log.info("Dev data: {} fee plans made", index);
	}

	// Pays every due before the day that makes the wanted status. No such day (or ON_TIME): every due up to today.
	private void payUpTo(Long studentId, FeePlanResponse plan, String wanted, LocalDate today, PaymentMode mode) {
		LocalDate stopAt = null;
		for (DueResponse due : plan.dues()) {
			long late = ChronoUnit.DAYS.between(due.dueOn(), today);
			boolean fits = switch (wanted) {
				case "DELAYED" -> late >= 11 && late <= 60;
				case "DEFAULTED" -> late > 60;
				default -> false;
			};
			if (fits && (stopAt == null || due.dueOn().isBefore(stopAt))) {
				stopAt = due.dueOn();
			}
		}
		EnumMap<FeeHead, BigDecimal> money = new EnumMap<>(FeeHead.class);
		for (DueResponse due : plan.dues()) {
			boolean before = (stopAt == null) ? !due.dueOn().isAfter(today) : due.dueOn().isBefore(stopAt);
			if (before) {
				money.merge(due.feeHead(), due.amount(), BigDecimal::add);
			}
		}
		List<PaymentLine> lines = money.entrySet().stream().map(e -> new PaymentLine(e.getKey(), e.getValue())).toList();
		if (!lines.isEmpty()) {
			paymentService.record(studentId, new PaymentRequest(null, mode, null, lines), null);
		}
	}

	private void loadEnquiries() {
		LocalDate today = LocalDate.now(clock);
		List<Long> students = studentService.list(new StudentFilter(null, null, null, null, null, null), 0, 4, null)
			.items()
			.stream()
			.map(item -> item.id())
			.toList();
		int admitted = 0;
		for (int i = 0; i < PARENTS.length; i++) {
			EnquiryStatus stage = STAGES[i];
			EnquirySource source = SOURCES[i % SOURCES.length];
			// Enquiries 8 to 12 (all CONTACTED) have a next date in the past: 5 overdue.
			LocalDate next = (i >= 8 && i <= 12) ? today.minusDays(1 + i % 5)
					: (stage.isOpen() && stage != EnquiryStatus.NEW) ? today.plusDays(2 + i % 6) : null;
			EnquiryResponse created = enquiryService.create(new EnquiryRequest(PARENTS[i],
					"98222" + String.format("%05d", i + 1), (i % 2 == 0) ? "FATHER" : "MOTHER", VILLAGES[i % VILLAGES.length],
					(i % 3 == 0) ? null : "Child " + (i + 1), CLASSES[i % CLASSES.length], null, null, source,
					(source == EnquirySource.REFERRAL) ? "Dharampal Goyal" : null, null,
					(i % 3 == 0) ? NeedsBus.YES : (i % 3 == 1) ? NeedsBus.UNKNOWN : NeedsBus.NO, next, null, null),
					null);
			if (stage == EnquiryStatus.NEW) {
				continue;
			}
			// The path to each stage uses only moves a person may make (no stage skipped twice).
			if (stage == EnquiryStatus.CONTACTED) {
				move(created.id(), EnquiryStatus.CONTACTED, null);
			}
			else if (stage == EnquiryStatus.VISITED) {
				move(created.id(), EnquiryStatus.VISITED, null);
			}
			else if (stage == EnquiryStatus.APPLIED || stage == EnquiryStatus.ADMITTED) {
				move(created.id(), EnquiryStatus.VISITED, null);
				move(created.id(), EnquiryStatus.APPLIED, null);
				if (stage == EnquiryStatus.ADMITTED && admitted < students.size()) {
					enquiryService.markAdmitted(created.id(), students.get(admitted++));
				}
			}
			else if (stage == EnquiryStatus.LOST) {
				move(created.id(), EnquiryStatus.LOST, "Joined a school near home");
			}
			if (stage == EnquiryStatus.VISITED && i == 15) {
				enquiryService.addFollowUp(created.id(), new FollowUpRequest("Visited the school with the father",
						today.plusDays(3)), null);
			}
		}
		log.info("Dev data loaded: {} enquiries", PARENTS.length);
	}

	private void move(Long id, EnquiryStatus status, String lostReason) {
		enquiryService.changeStatus(id, new StatusRequest(status, lostReason));
	}

	// ---- taps (Phase 4) ----

	// Per route: the time the first child is tapped at BOARDED_MORNING (null = no taps), and whether the bus
	// reached school at 07:46. The next child is tapped a minute later, and the last child (when there are 3 or
	// more) is ABSENT.
	private static final Map<Integer, String> FIRST_TAP = Map.of(1, "07:31", 2, "07:23", 4, "07:11", 5, "07:40",
			6, "07:41", 8, "07:33");

	private static final int REACHED_SCHOOL_ROUTE = 2;

	private static final String REACHED_AT = "07:46";

	private void loadTaps() {
		LocalDate today = LocalDate.now(clock);
		if (markService.anyTapOn(today)) {
			log.info("Dev data: there are taps today already, taps not loaded");
			return;
		}
		Long officeUser = userService.list()
			.stream()
			.filter(u -> u.active() && u.role().has(Permission.TRIPS_RECORD_ANY))
			.map(UserResponse::id)
			.findFirst()
			.orElse(null);
		if (officeUser == null) {
			log.info("Dev data: there is no office user yet (set APP_OWNER_PHONE), taps not loaded");
			return;
		}
		List<MarkRequest> marks = new ArrayList<>();
		for (RouteResponse route : routeService.list()) {
			int number = routeNumber(route.name());
			String first = FIRST_TAP.get(number);
			if (first == null) {
				continue;
			}
			List<RouteChild> children = studentQuery.onRoute(route.id(), today);
			for (int i = 0; i < children.size(); i++) {
				boolean absent = children.size() >= 3 && i == children.size() - 1;
				Outcome outcome = absent ? Outcome.ABSENT : Outcome.DONE;
				marks.add(new MarkRequest(children.get(i).studentId(), EventType.BOARDED_MORNING, outcome, today,
						at(today, LocalTime.parse(first).plusMinutes(i))));
				if (number == REACHED_SCHOOL_ROUTE && !absent) {
					marks.add(new MarkRequest(children.get(i).studentId(), EventType.REACHED_SCHOOL, outcome, today,
							at(today, LocalTime.parse(REACHED_AT))));
				}
			}
		}
		List<MarkResult> results = markService.apply(officeUser, marks);
		log.info("Dev data loaded: {} taps for the morning of {} ({} refused)", marks.size(), today,
				results.stream().filter(r -> !r.ok()).count());
	}

	private java.time.Instant at(LocalDate day, LocalTime time) {
		return day.atTime(time).atZone(clock.getZone()).toInstant();
	}

	// "Route 4" → 4
	private static int routeNumber(String name) {
		return Integer.parseInt(name.replaceAll("\\D", ""));
	}

	// 1 April is the start of the school year.
	private static LocalDate schoolYearStart(LocalDate today) {
		return LocalDate.of((today.getMonthValue() >= 4) ? today.getYear() : today.getYear() - 1, 4, 1);
	}

}
