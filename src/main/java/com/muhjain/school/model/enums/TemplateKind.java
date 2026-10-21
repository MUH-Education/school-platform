package com.muhjain.school.model.enums;

/**
 * What a {@code message_template} row is for. SYSTEM = the bus, receipt and reminder texts of Phase 5 (found by
 * code). The others match {@link NotificationKind} one to one. Set when the template is made, never changed.
 */
public enum TemplateKind {

	SYSTEM, NOTICE, URGENT, MARKETING, GREETING, FEE_REMINDER, ONE_FAMILY, STAFF;

	/** The template kind that a notification of this kind must use. Example: MARKETING → MARKETING. */
	public static TemplateKind of(NotificationKind kind) {
		return valueOf(kind.name());
	}

}
