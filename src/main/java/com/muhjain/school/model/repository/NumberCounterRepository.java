package com.muhjain.school.model.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code number_counter} table: one row per kind and year, {@code last_no} is the last number given.
 * Replaces the old {@code admission_counter} and {@code receipt_counter} tables; also used for TC and
 * refund numbers. Example: counter key "ADMISSION-2026", last_no 118.
 */
@Repository
public class NumberCounterRepository {

	private final JdbcClient jdbc;

	public NumberCounterRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Adds 1 and returns the new number, in one SQL statement. The row stays locked until the calling
	 * transaction ends, so a second clerk waits and then gets the next number. A key with no row starts at 1.
	 * Example: "RECEIPT-2026", last_no 1188 → 1189.
	 */
	public int next(String counterKey) {
		Integer number = jdbc.sql("""
				insert into number_counter (counter_key, last_no) values (:key, 1)
				on conflict (counter_key) do update set last_no = number_counter.last_no + 1, updated_at = now()
				returning last_no
				""").param("key", counterKey).query(Integer.class).single();
		return number;
	}

}
