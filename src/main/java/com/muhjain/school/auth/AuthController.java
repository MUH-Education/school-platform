package com.muhjain.school.auth;

import java.time.Clock;
import java.time.LocalDate;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.route.AttendantRouteService;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login with phone and OTP. See docs/04-login-otp-jwt.md.
 * <ul>
 * <li>{@code POST /api/v1/auth/otp/request} — open</li>
 * <li>{@code POST /api/v1/auth/otp/verify} — open</li>
 * <li>{@code GET /api/v1/auth/me} — any login</li>
 * <li>{@code POST /api/v1/auth/logout} — any login</li>
 * </ul>
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final OtpService otpService;

	private final UserService userService;

	private final CurrentUser currentUser;

	private final AttendantRouteService attendantRoutes;

	private final Clock clock;

	public AuthController(OtpService otpService, UserService userService, CurrentUser currentUser,
			AttendantRouteService attendantRoutes, Clock clock) {
		this.attendantRoutes = attendantRoutes;
		this.clock = clock;
		this.otpService = otpService;
		this.userService = userService;
		this.currentUser = currentUser;
	}

	@Operation(summary = "Ask for a login code")
	@SecurityRequirements
	@PostMapping("/otp/request")
	public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request, HttpServletRequest http) {
		return otpService.request(request.phone(), http.getRemoteAddr());
	}

	@Operation(summary = "Check the code and get a token")
	@SecurityRequirements
	@PostMapping("/otp/verify")
	public LoginResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		return otpService.verify(request.phone(), request.otp());
	}

	@Operation(summary = "Who am I and what can I do")
	@GetMapping("/me")
	public AuthUserResponse me() {
		return userService.findById(currentUser.id())
			.map(this::withRoute)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please log in first."));
	}

	@Operation(summary = "Log out (the token stops working)")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout() {
		userService.logout(currentUser.id());
	}

	// The attendant's route of today, from the database (never from the client). Null if they have none.
	private AuthUserResponse withRoute(AppUser user) {
		AuthUserResponse.RouteInfo route = attendantRoutes.routeFor(user.getId(), LocalDate.now(clock))
			.map(r -> new AuthUserResponse.RouteInfo(r.routeId(), r.routeName(), r.vehicleName()))
			.orElse(null);
		return AuthUserResponse.of(user, route);
	}

}
