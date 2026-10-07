package com.muhjain.school.route;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.muhjain.school.staff.AssignmentService;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.UserService;
import com.muhjain.school.vehicle.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Which route is this attendant on today?" This is the third query of "Three queries used everywhere" in
 * docs/03-data-model.md, and the base of the attendant rule in docs/05-roles-permissions.md ("Attendant scope").
 * <p>
 * Phase 4 calls it for every trip URL. The route always comes from here, never from the request.
 * Example: Balwan's user → staff 14 → he is the ATTENDANT of Van 4 today → Van 4 runs Route 4.
 * If any step has no answer, the attendant has no route today, and Phase 4 answers 403 NOT_YOUR_ROUTE.
 */
@Service
public class AttendantRouteService {

	private final UserService userService;

	private final AssignmentService assignmentService;

	private final RouteService routeService;

	private final VehicleService vehicleService;

	public AttendantRouteService(UserService userService, AssignmentService assignmentService,
			RouteService routeService, VehicleService vehicleService) {
		this.userService = userService;
		this.assignmentService = assignmentService;
		this.routeService = routeService;
		this.vehicleService = vehicleService;
	}

	/**
	 * @param userId the logged-in user, from the token (never from the request)
	 * @param date the school day
	 * @return the active route of the vehicle where the user's staff member is the ATTENDANT on that day.
	 * Empty if: no such user, the user is turned off, the user has no staff row, the person is on no vehicle that
	 * day (also when a replacement covers them), or the vehicle has no active route.
	 */
	@Transactional(readOnly = true)
	public Optional<AttendantRoute> routeFor(Long userId, LocalDate date) {
		return userService.findById(userId)
			.filter(AppUser::isActive)
			.map(AppUser::getStaffId)
			.flatMap(staffId -> assignmentService.attendantVehicleOn(staffId, date))
			.flatMap(vehicleId -> routeService.activeRouteOfVehicle(vehicleId)
				.map(route -> new AttendantRoute(route.id(), route.name(), vehicleId,
						vehicleService.names(List.of(vehicleId)).get(vehicleId))));
	}

}
