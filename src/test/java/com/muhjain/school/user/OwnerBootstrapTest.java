package com.muhjain.school.user;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerBootstrapTest extends AbstractIntegrationTest {

	@Autowired
	private UserService userService;

	@Test
	void emptyTableAndPhoneSetCreatesOneOwnerOnce() {
		OwnerBootstrap bootstrap = new OwnerBootstrap(userService, "98123 40001");

		bootstrap.run(new DefaultApplicationArguments());
		bootstrap.run(new DefaultApplicationArguments());

		assertThat(userRepository.findAll()).singleElement().satisfies(owner -> {
			assertThat(owner.getPhone()).isEqualTo("+919812340001");
			assertThat(owner.getRole()).isEqualTo(Role.OWNER);
			assertThat(owner.isActive()).isTrue();
		});
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'CREATED'", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void existingUsersMeanNothingIsCreated() {
		addUser("+919812340002", Role.OFFICE_ADMIN);

		new OwnerBootstrap(userService, "98123 40001").run(new DefaultApplicationArguments());

		assertThat(userRepository.findByPhone("+919812340001")).isEmpty();
	}

	@Test
	void noPhoneMeansNothingIsCreated() {
		new OwnerBootstrap(userService, "").run(new DefaultApplicationArguments());

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void wrongPhoneStopsTheApp() {
		assertThatThrownBy(() -> new OwnerBootstrap(userService, "12345").run(new DefaultApplicationArguments()))
			.isInstanceOf(ApiException.class);
	}

}
