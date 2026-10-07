package com.muhjain.school.trip;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.route.AttendantRoute;
import com.muhjain.school.route.AttendantRouteService;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Permission;
import com.muhjain.school.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * One place that answers "may this user touch this route on this date?" (rules 1 to 3 of the phase file).
 * Every trip endpoint goes through it.
 * <ul>
 * <li>Office (has TRIPS_RECORD_ANY): any route, any date.</li>
 * <li>Attendant (TRIPS_RECORD only): only the route that {@link AttendantRouteService} finds for them on that
 * day. Reading: today only. Writing: today and yesterday (a phone may have been offline overnight).</li>
 * </ul>
 * The route is never taken from the client. Example: Balwan's route today is Route 4. He asks for Route 7 →
 * {@code NOT_YOUR_ROUTE}.
 * <p>
 * The permission comes from the user's row in the database (their role), not from the token.
 */
@Component
public class TripAccess {

	public static final String NOT_YOUR_ROUTE = "NOT_YOUR_ROUTE";

	public static final String DATE_NOT_ALLOWED = "DATE_NOT_ALLOWED";

	private final UserService userService;

	private final AttendantRouteService attendantRoutes;

	private final Clock clock;

	public TripAccess(UserService userService, AttendantRouteService attendantRoutes, Clock clock) {
		this.userService = userService;
		this.attendantRoutes = attendantRoutes;
		this.clock = clock;
	}

	/**
	 * Looks at the user once. Use the answer for every tap of one request.
	 *
	 * @param userId the logged-in user, from the token
	 * @throws ApiException 401 UNAUTHENTICATED if the user is gone or turned off
	 */
	public Access forUser(Long userId) {
		AppUser user = userService.findById(userId)
			.filter(AppUser::isActive)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please log in first."));
		return new Access(userId, user.getRole().has(Permission.TRIPS_RECORD_ANY));
	}

	public final class Access {

		private final Long userId;

		private final boolean office;

		// One question to the assignment tables per day, not one per tap.
		private final Map<LocalDate, Optional<AttendantRoute>> routes = new HashMap<>();

		private Access(Long userId, boolean office) {
			this.userId = userId;
			this.office = office;
		}

		/** True for a user with TRIPS_RECORD_ANY. */
		public boolean isOffice() {
			return office;
		}

		/** The attendant's own route on that day. Empty if they work on no route that day. */
		public Optional<AttendantRoute> ownRoute(LocalDate date) {
			return routes.computeIfAbsent(date, day -> attendantRoutes.routeFor(userId, day));
		}

		/**
		 * Why this user may not WRITE on this route and day. Empty = allowed.
		 * The route is checked first, then the date (same order as rule 5).
		 * Example: attendant of Route 4, tap for Route 7 → NOT_YOUR_ROUTE. Tap for Route 4 three days ago →
		 * DATE_NOT_ALLOWED.
		 */
		public Optional<String> writeDenial(Long routeId, LocalDate date) {
			if (office) {
				return Optional.empty();
			}
			if (!isOwn(routeId, date)) {
				return Optional.of(NOT_YOUR_ROUTE);
			}
			LocalDate today = LocalDate.now(clock);
			if (!date.equals(today) && !date.equals(today.minusDays(1))) {
				return Optional.of(DATE_NOT_ALLOWED);
			}
			return Optional.empty();
		}

		/** Same for READING. An attendant reads only today. */
		public Optional<String> readDenial(Long routeId, LocalDate date) {
			if (office) {
				return Optional.empty();
			}
			if (!isOwn(routeId, date)) {
				return Optional.of(NOT_YOUR_ROUTE);
			}
			if (!date.equals(LocalDate.now(clock))) {
				return Optional.of(DATE_NOT_ALLOWED);
			}
			return Optional.empty();
		}

		/** @throws ApiException 403 with the code of {@link #readDenial} */
		public void requireRead(Long routeId, LocalDate date) {
			readDenial(routeId, date).ifPresent(TripAccess::forbid);
		}

		/** @throws ApiException 403 with the code of {@link #writeDenial} */
		public void requireWrite(Long routeId, LocalDate date) {
			writeDenial(routeId, date).ifPresent(TripAccess::forbid);
		}

		private boolean isOwn(Long routeId, LocalDate date) {
			return routeId != null && ownRoute(date).map(AttendantRoute::routeId).filter(routeId::equals).isPresent();
		}

	}

	private static void forbid(String code) {
		String message = NOT_YOUR_ROUTE.equals(code) ? "This is not your route today."
				: "You can only use today (or yesterday for saving taps).";
		throw new ApiException(HttpStatus.FORBIDDEN, code, message);
	}

}
