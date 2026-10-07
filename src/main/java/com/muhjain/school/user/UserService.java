package com.muhjain.school.user;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.staff.StaffService;
import com.muhjain.school.staff.StaffType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users: find, add, change, turn off. Other features use this service, never {@link AppUserRepository}.
 * Rules are in docs/phases/phase-1-login-users.md (rules 1, 2, 6, 7, 8) and docs/05-roles-permissions.md.
 */
@Service
public class UserService {

	static final String ENTITY = "USER";

	private final AppUserRepository users;

	private final AuditService auditService;

	private final StaffService staffService;

	private final Clock clock;

	public UserService(AppUserRepository users, AuditService auditService, StaffService staffService, Clock clock) {
		this.users = users;
		this.auditService = auditService;
		this.staffService = staffService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> list() {
		return users.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
	}

	/** The fixed table from {@link Role}. Roles are not stored in the database. */
	public RolesResponse roles() {
		List<RolesResponse.RoleRow> rows = Arrays.stream(Role.values())
			.map(role -> new RolesResponse.RoleRow(role, List.copyOf(role.permissions())))
			.toList();
		return new RolesResponse(List.of(Permission.values()), rows);
	}

	/**
	 * Rule 1 and 2. Example: {@code { "phone": "98123 40002", "role": "OFFICE_ADMIN" }} → user 2.
	 *
	 * @param actorId the logged-in user who adds, saved as {@code created_by}
	 * @throws ApiException 409 PHONE_ALREADY_USED, 400 VALIDATION for a bad phone or staff id
	 */
	@Transactional
	public UserResponse create(CreateUserRequest request, Long actorId) {
		String phone = PhoneNumbers.normalize(request.phone());
		checkStaffId(request.role(), request.staffId());
		if (users.existsByPhone(phone)) {
			throw phoneAlreadyUsed();
		}
		AppUser user = new AppUser(phone, request.role());
		user.setName(clean(request.name()));
		user.setStaffId(request.staffId());
		user.setCreatedBy(actorId);
		user = saveCheckingPhone(user);
		auditService.record(ENTITY, user.getId(), AuditAction.CREATED,
				"User " + phone + " added as " + user.getRole(), null);
		return toResponse(user);
	}

	/**
	 * Change phone, name, role, staff id, active. Rules 6, 7, 8.
	 * Example: Neelam's role OFFICE_ADMIN → ADMISSIONS_DESK. Her token stops working, she logs in again.
	 *
	 * @param actorId the logged-in user who changes
	 * @throws ApiException 404 NOT_FOUND, 409 PHONE_ALREADY_USED, 409 LAST_OWNER, 409 CANNOT_DISABLE_SELF
	 */
	@Transactional
	public UserResponse update(Long id, UpdateUserRequest request, Long actorId) {
		AppUser user = users.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This user does not exist."));
		String phone = PhoneNumbers.normalize(request.phone());
		Role role = request.role();
		boolean active = request.active();
		checkStaffId(role, request.staffId());

		if (!phone.equals(user.getPhone()) && users.existsByPhoneAndIdNot(phone, id)) {
			throw phoneAlreadyUsed();
		}
		if (id.equals(actorId) && !active) {
			throw new ApiException(HttpStatus.CONFLICT, "CANNOT_DISABLE_SELF", "You cannot turn off your own account.");
		}
		boolean losesOwner = user.isActive() && user.getRole() == Role.OWNER && (role != Role.OWNER || !active);
		if (losesOwner && users.findByRoleAndActiveTrue(Role.OWNER).size() <= 1) {
			throw new ApiException(HttpStatus.CONFLICT, "LAST_OWNER",
					"This is the last active owner. Add another owner first.");
		}

		Map<String, Object> changes = new LinkedHashMap<>();
		change(changes, "phone", user.getPhone(), phone);
		change(changes, "name", user.getName(), clean(request.name()));
		change(changes, "role", user.getRole(), role);
		change(changes, "staffId", user.getStaffId(), request.staffId());
		change(changes, "active", user.isActive(), active);
		if (changes.isEmpty()) {
			return toResponse(user);
		}
		// Rule 6: phone change, role change, turn off → old tokens stop working.
		if (changes.containsKey("phone") || changes.containsKey("role") || (user.isActive() && !active)) {
			user.bumpTokenVersion();
		}
		user.setPhone(phone);
		user.setName(clean(request.name()));
		user.setRole(role);
		user.setStaffId(request.staffId());
		user.setActive(active);
		user = saveCheckingPhone(user);
		auditService.record(ENTITY, user.getId(), AuditAction.UPDATED, summary(changes), changes);
		return toResponse(user);
	}

	/**
	 * Rule 9: the very first user. Only when the table is empty.
	 * Example: APP_OWNER_PHONE=9812340001 and no users → OWNER +919812340001.
	 *
	 * @return the new owner, or empty if users already exist
	 */
	@Transactional
	public Optional<AppUser> createFirstOwnerIfNoUsers(String rawPhone) {
		String phone = PhoneNumbers.normalize(rawPhone);
		if (users.count() > 0) {
			return Optional.empty();
		}
		AppUser owner = users.saveAndFlush(new AppUser(phone, Role.OWNER));
		auditService.record(ENTITY, owner.getId(), AuditAction.CREATED, "First owner " + phone + " created at start",
				null, null);
		return Optional.of(owner);
	}

	/**
	 * Logout: every token of this user, on every device, stops working.
	 * Example: Neelam logs out on the office computer → her phone is logged out too.
	 */
	@Transactional
	public void logout(Long userId) {
		AppUser user = users.findById(userId).orElseThrow();
		user.bumpTokenVersion();
		auditService.record(ENTITY, user.getId(), AuditAction.UPDATED, "Logged out on all devices", null);
	}

	/**
	 * Names for "who changed this". A user with no name shows a masked phone. Example: {2 → "Neelam", 5 → "+91XXXXXX0005"}.
	 * An id that does not exist is not in the map.
	 */
	@Transactional(readOnly = true)
	public java.util.Map<Long, String> displayNames(java.util.Collection<Long> ids) {
		if (ids.isEmpty()) {
			return java.util.Map.of();
		}
		return users.findAllById(ids)
			.stream()
			.collect(java.util.stream.Collectors.toMap(AppUser::getId, u -> (u.getName() != null && !u.getName().isBlank())
					? u.getName() : com.muhjain.school.common.PhoneNumbers.mask(u.getPhone())));
	}

	/** Example: 2 → Neelam's row, active or not. */
	@Transactional(readOnly = true)
	public Optional<AppUser> findById(Long id) {
		return users.findById(id);
	}

	/** Sets "last login" to now. Called after a right OTP. */
	@Transactional
	public AppUser recordLogin(Long userId, Instant now) {
		AppUser user = users.findById(userId).orElseThrow();
		user.setLastLoginAt(now);
		return user;
	}

	/** Example: "+919812340002" → Neelam, if she is active. A turned-off user is not found. */
	@Transactional(readOnly = true)
	public Optional<AppUser> findActiveByPhone(String phone) {
		return users.findByPhoneAndActiveTrue(phone);
	}

	// An ATTENDANT must point at a real staff row of type ATTENDANT (so the server knows the route).
	// Other roles have none. Example: staffId 14 is Balwan, ATTENDANT → fine. Staff 21 is a DRIVER → 400.
	private void checkStaffId(Role role, Long staffId) {
		if (role == Role.ATTENDANT && staffId == null) {
			throw ApiException.validation("staffId", "is required for an attendant");
		}
		if (role != Role.ATTENDANT && staffId != null) {
			throw ApiException.validation("staffId", "is only for an attendant");
		}
		if (role == Role.ATTENDANT) {
			StaffType type = staffService.findType(staffId)
				.orElseThrow(() -> ApiException.validation("staffId", "does not exist"));
			if (type != StaffType.ATTENDANT) {
				throw ApiException.validation("staffId", "must be a staff member of type ATTENDANT");
			}
		}
	}

	// The check above can miss a user added at the same moment. The unique index still catches it.
	private AppUser saveCheckingPhone(AppUser user) {
		try {
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw phoneAlreadyUsed();
		}
	}

	private static ApiException phoneAlreadyUsed() {
		return new ApiException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED", "Another user already has this phone.");
	}

	private static String clean(String name) {
		return (name == null || name.isBlank()) ? null : name.strip();
	}

	private static void change(Map<String, Object> changes, String field, Object oldValue, Object newValue) {
		if (!Objects.equals(oldValue, newValue)) {
			Map<String, Object> pair = new LinkedHashMap<>();
			pair.put("old", (oldValue instanceof Enum<?> e) ? e.name() : oldValue);
			pair.put("new", (newValue instanceof Enum<?> e) ? e.name() : newValue);
			changes.put(field, pair);
		}
	}

	// Example: "Role changed from ADMISSIONS_DESK to OFFICE_ADMIN. Turned off."
	@SuppressWarnings("unchecked")
	private static String summary(Map<String, Object> changes) {
		StringBuilder text = new StringBuilder();
		changes.forEach((field, value) -> {
			Map<String, Object> pair = (Map<String, Object>) value;
			if (field.equals("active")) {
				text.append(Boolean.TRUE.equals(pair.get("new")) ? "Turned on. " : "Turned off. ");
			}
			else {
				String label = Character.toUpperCase(field.charAt(0)) + field.substring(1);
				text.append(label).append(" changed from ").append(pair.get("old")).append(" to ")
					.append(pair.get("new")).append(". ");
			}
		});
		return text.toString().strip();
	}

	private UserResponse toResponse(AppUser user) {
		return UserResponse.of(user, clock.getZone());
	}

}
