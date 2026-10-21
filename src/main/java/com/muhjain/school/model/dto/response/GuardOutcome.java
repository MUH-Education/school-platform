package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.SkipReason;

import java.time.Instant;
import java.util.Map;

/**
 * What the guard chain says about one recipient. {@code skipReason} null = may go.
 * Example: may go, values with "Amount due": "₹8,500", not before 8:00 tomorrow (quiet hours).
 *
 * @param values the recipient's values with what the guards filled in (the live fee numbers)
 * @param holdUntil do not send before this moment; null = at once
 */
public record GuardOutcome(SkipReason skipReason, Map<String, String> values, Instant holdUntil) {

	public boolean mayGo() {
		return skipReason == null;
	}

}
