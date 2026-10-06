package com.muhjain.school;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationV1Test extends AbstractIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void settingsHaveTheirStartValues() {
		assertThat(jdbc.queryForObject("select value from app_setting where key = 'transport.bus_fee_per_year'",
				String.class))
			.isEqualTo("8800");
		assertThat(jdbc.queryForObject("select count(*) from app_setting", Integer.class)).isEqualTo(7);
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

	@Test
	void unknownOtpChannelIsRejected() {
		assertThatThrownBy(() -> jdbc.update("insert into otp_code (phone, code_hash, channel, expires_at) "
				+ "values ('+919812340002', 'x', 'EMAIL', now())"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
