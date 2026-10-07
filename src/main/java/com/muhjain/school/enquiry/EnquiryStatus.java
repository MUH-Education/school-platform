package com.muhjain.school.enquiry;

import java.util.EnumSet;
import java.util.Set;

/**
 * The stages: NEW → CONTACTED → VISITED → APPLIED → ADMITTED, and LOST from any stage.
 * ADMITTED is never set by hand: only an admission made from the enquiry sets it.
 */
public enum EnquiryStatus {

	NEW, CONTACTED, VISITED, APPLIED, ADMITTED, LOST;

	/** The two end states. An enquiry in one of them is closed, never overdue, and may be asked again. */
	public static final Set<EnquiryStatus> CLOSED = EnumSet.of(ADMITTED, LOST);

	public boolean isOpen() {
		return !CLOSED.contains(this);
	}

}
