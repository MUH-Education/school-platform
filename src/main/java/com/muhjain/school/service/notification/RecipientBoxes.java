package com.muhjain.school.service.notification;

import java.util.Set;

/**
 * The {box} names the recipients fill by themselves (docs/10-notifications.md section 3). Any other box in a
 * notification's text needs a fixed value, the same for everyone. Example: "{Date}" → {"Date": "10 अक्टूबर"}.
 */
public final class RecipientBoxes {

	public static final Set<String> STANDARD = Set.of("Name", "Parent name", "Child name", "Class", "Route",
			"Staff name", "Amount due", "Months", "Months late", "Late fee");

	private RecipientBoxes() {
	}

}
