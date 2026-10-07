package com.muhjain.school.fee;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code receipt_counter} table: one row per session, {@code last_no} is the last receipt number given.
 * Example: session 2026-27, last_no 411.
 */
@Repository
public class ReceiptCounterRepository {

	private final JdbcClient jdbc;

	public ReceiptCounterRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Adds 1 and returns the new number, in one SQL statement. The row stays locked until the calling transaction
	 * ends, so a second clerk waits and then gets the next number. A session with no row starts at 1.
	 * Example: last_no 411 → 412.
	 */
	public int next(long sessionId) {
		Integer number = jdbc.sql("""
				insert into receipt_counter (session_id, last_no) values (:sessionId, 1)
				on conflict (session_id) do update set last_no = receipt_counter.last_no + 1, updated_at = now()
				returning last_no
				""").param("sessionId", sessionId).query(Integer.class).single();
		return number;
	}

}
