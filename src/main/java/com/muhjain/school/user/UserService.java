package com.muhjain.school.user;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users: find, add, change, turn off. Other features use this service, never {@link AppUserRepository}.
 */
@Service
public class UserService {

	private final AppUserRepository users;

	public UserService(AppUserRepository users) {
		this.users = users;
	}

	/** Example: "+919812340002" → Neelam, if she is active. A turned-off user is not found. */
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
