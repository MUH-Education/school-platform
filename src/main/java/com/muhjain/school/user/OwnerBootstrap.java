package com.muhjain.school.user;

import com.muhjain.school.common.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The first user. Only an OWNER can add users, but at the start there is no user.
 * If {@code app_user} is empty and APP_OWNER_PHONE is set, this creates one OWNER with that phone.
 * Once any user exists it does nothing. A wrong phone stops the app, so the mistake is seen at once.
 */
// Runs first, so the dev data loader (which needs one office user for the taps) finds the owner.
@Component
@Order(0)
public class OwnerBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

	private final UserService userService;

	private final String ownerPhone;

	public OwnerBootstrap(UserService userService, @Value("${app.bootstrap.owner-phone:}") String ownerPhone) {
		this.userService = userService;
		this.ownerPhone = ownerPhone;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (ownerPhone == null || ownerPhone.isBlank()) {
			return;
		}
		userService.createFirstOwnerIfNoUsers(ownerPhone)
			.ifPresent(owner -> log.info("Created first owner {}", PhoneNumbers.mask(owner.getPhone())));
	}

}
