package com.muhjain.school;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStartsTest extends AbstractIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void contextLoads() {
	}

	@Test
	void usesTestcontainersPostgresWithFlyway() {
		assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 17");
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '0' and success",
				Integer.class))
			.isEqualTo(1);
	}

}
