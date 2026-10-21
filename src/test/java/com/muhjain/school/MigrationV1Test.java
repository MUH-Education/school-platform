package com.muhjain.school;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationV1Test extends AbstractIntegrationTest {

	@Test
	void settingsHaveTheirStartValues() {
		assertThat(jdbc.queryForObject("select value from app_setting where key = 'transport.months_operated'",
				String.class))
			.isEqualTo("11");
		// V1 seeds 11 settings. Later migrations add their own (V14: 3 notifications.* keys, see MigrationV14Test).
		assertThat(jdbc.queryForObject("select count(*) from app_setting where key not like 'notifications.%'",
				Integer.class))
			.isEqualTo(11);
	}

	@Test
	void phoneMustBeInPlus91Form() {
		assertThatThrownBy(() -> jdbc.update("insert into app_user (phone, role) values ('9812340002', 'OWNER')"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void attendantNeedsStaffId() {
		assertThatThrownBy(
				() -> jdbc.update("insert into app_user (phone, role) values ('+919812340009', 'ATTENDANT')"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
