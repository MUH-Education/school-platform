package com.muhjain.school.user;

import java.time.Instant;
import java.util.Optional;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users: find, add, change, turn off. Other features use this service, never {@link AppUserRepository}.
 */
@Service
public class UserService {

	static final String ENTITY = "USER";

	private final AppUserRepository users;

	private final AuditService auditService;

	public UserService(AppUserRepository users, AuditService auditService) {
		this.users = users;
		this.auditService = auditService;
	}

	/** Example: "+919812340002" → Neelam, if she is active. A turned-off user is not found. */
	/** Example: 2 → Neelam's row, active or not. */
	@Transactional(readOnly = true)
	public Optional<AppUser> findById(Long id) {
		return users.findById(id);
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

	/** Sets "last login" to now. Called after a right OTP. */
	@Transactional
	public AppUser recordLogin(Long userId, Instant now) {
		AppUser user = users.findById(userId).orElseThrow();
		user.setLastLoginAt(now);
		return user;
	}

	@Transactional(readOnly = true)
	public Optional<AppUser> findActiveByPhone(String phone) {
		return users.findByPhoneAndActiveTrue(phone);
	}

}
