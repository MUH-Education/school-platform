package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.enums.AudienceGroup;

import java.util.List;
import java.util.Set;

/**
 * Finds the people of one or more audience groups (docs/10-notifications.md section 3), today, from the live data.
 * All resolvers answer the same {@link Recipient} shape, so {@link AudienceService} never cares which group it is
 * reading. To add a group: one enum value and one class (or one more group in an existing class).
 * Example: {@code StaffResolver} serves STAFF_ALL, DRIVERS, ATTENDANTS and TEACHERS.
 */
public interface RecipientResolver {

	/** The groups this class answers. Every group has exactly one resolver; the app does not start otherwise. */
	Set<AudienceGroup> groups();

	/**
	 * The people of the group, today. A person with no phone is included with {@code phone = null} (it is counted,
	 * then dropped). Duplicates and the stop list are handled by {@link AudienceService}, not here.
	 */
	List<Recipient> resolve(AudienceGroup group, AudienceFilter filter);

}
