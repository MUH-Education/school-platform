package com.muhjain.school.trip;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.route.AttendantRoute;
import com.muhjain.school.route.AttendantRouteService;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import com.muhjain.school.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Rules 1 to 3. Pure test: users and routes are mocks. Today is 7 Oct 2026. Balwan (user 1) is on Route 4. */
class TripAccessTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	private final UserService users = mock(UserService.class);

	private final AttendantRouteService routes = mock(AttendantRouteService.class);

	private TripAccess access;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-07T02:30:00Z"), ZoneId.of("Asia/Kolkata"));
		access = new TripAccess(users, routes, clock);
		when(users.findById(1L)).thenReturn(Optional.of(user(Role.ATTENDANT, true)));
		when(users.findById(2L)).thenReturn(Optional.of(user(Role.OFFICE_ADMIN, true)));
		when(routes.routeFor(eq(1L), any())).thenReturn(Optional.of(new AttendantRoute(4L, "Route 4", 4L, "Van 4")));
	}

	private static AppUser user(Role role, boolean active) {
		AppUser user = new AppUser("+919812340011", role);
		user.setActive(active);
		return user;
	}

	@Test
	void attendantCannotOpenAnotherRoute() {
		var attendant = access.forUser(1L);
		assertThat(attendant.readDenial(7L, TODAY)).contains("NOT_YOUR_ROUTE");
		assertThat(attendant.writeDenial(7L, TODAY)).contains("NOT_YOUR_ROUTE");
		assertThatThrownBy(() -> attendant.requireRead(7L, TODAY)).isInstanceOf(ApiException.class)
			.hasFieldOrPropertyWithValue("code", "NOT_YOUR_ROUTE");
	}

	@Test
	void attendantUsesOwnRouteToday() {
		var attendant = access.forUser(1L);
		assertThat(attendant.readDenial(4L, TODAY)).isEmpty();
		assertThat(attendant.writeDenial(4L, TODAY)).isEmpty();
	}

	@Test
	void attendantMayWriteYesterdayButNotReadIt() {
		var attendant = access.forUser(1L);
		assertThat(attendant.writeDenial(4L, TODAY.minusDays(1))).isEmpty();
		assertThat(attendant.readDenial(4L, TODAY.minusDays(1))).contains("DATE_NOT_ALLOWED");
	}

	@Test
	void attendantCannotWriteThreeDaysAgoOrTomorrow() {
		var attendant = access.forUser(1L);
		assertThat(attendant.writeDenial(4L, TODAY.minusDays(3))).contains("DATE_NOT_ALLOWED");
		assertThat(attendant.writeDenial(4L, TODAY.plusDays(1))).contains("DATE_NOT_ALLOWED");
	}

	@Test
	void wrongRouteIsReportedBeforeWrongDate() {
		assertThat(access.forUser(1L).writeDenial(7L, TODAY.minusDays(3))).contains("NOT_YOUR_ROUTE");
	}

	@Test
	void attendantWithNoRouteThatDayIsDenied() {
		when(routes.routeFor(eq(1L), any())).thenReturn(Optional.empty());
		assertThat(access.forUser(1L).writeDenial(4L, TODAY)).contains("NOT_YOUR_ROUTE");
	}

	@Test
	void nullRouteIsDenied() {
		assertThat(access.forUser(1L).writeDenial(null, TODAY)).contains("NOT_YOUR_ROUTE");
	}

	@Test
	void officeCanUseAnyRouteAndAnyDate() {
		var office = access.forUser(2L);
		assertThat(office.isOffice()).isTrue();
		assertThat(office.writeDenial(7L, TODAY.minusDays(30))).isEmpty();
		assertThat(office.readDenial(9L, TODAY.plusDays(2))).isEmpty();
	}

	@Test
	void routeOfTheDayIsAskedOncePerDay() {
		var attendant = access.forUser(1L);
		attendant.writeDenial(4L, TODAY);
		attendant.writeDenial(4L, TODAY);
		attendant.writeDenial(4L, TODAY.minusDays(1));
		verify(routes, times(2)).routeFor(eq(1L), any());
	}

	@Test
	void turnedOffOrUnknownUserIsUnauthenticated() {
		when(users.findById(3L)).thenReturn(Optional.of(user(Role.ATTENDANT, false)));
		assertThatThrownBy(() -> access.forUser(3L)).hasFieldOrPropertyWithValue("code", "UNAUTHENTICATED");
		assertThatThrownBy(() -> access.forUser(99L)).hasFieldOrPropertyWithValue("code", "UNAUTHENTICATED");
	}

}
