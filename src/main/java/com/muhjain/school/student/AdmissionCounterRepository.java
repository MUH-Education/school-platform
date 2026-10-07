package com.muhjain.school.student;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code admission_counter} table: one row per year, {@code last_no} is the last number given.
 * Example: year 2026, last_no 118.
 */
@Repository
public class AdmissionCounterRepository {

	private final JdbcClient jdbc;

	public AdmissionCounterRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Adds 1 and returns the new number, in one SQL statement. The row stays locked until the calling transaction
	 * ends, so a second clerk waits and then gets the next number. A year with no row starts at 1.
	 * Example: last_no 118 → 119.
	 */
	public int next(int year) {
		Integer number = jdbc.sql("""
				insert into admission_counter (year, last_no) values (:year, 1)
				on conflict (year) do update set last_no = admission_counter.last_no + 1, updated_at = now()
				returning last_no
				""").param("year", year).query(Integer.class).single();
		return number;
	}

}
