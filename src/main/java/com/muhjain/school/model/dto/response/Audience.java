package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.AudienceGroup;

import java.util.List;

/**
 * Who a notification goes to: the ticked groups and one shared filter. Stored as the {@code jsonb} column
 * {@code notification.audience}. It is the question, never the answer: the people are found again at send time.
 * Example: {@code {"groups":["CURRENT_PARENTS","STAFF_ALL"],"filter":{"classes":["10","12"]}}}.
 */
public record Audience(List<AudienceGroup> groups, AudienceFilter filter) {

	public Audience {
		groups = (groups == null) ? List.of() : List.copyOf(groups);
		filter = (filter == null) ? AudienceFilter.NONE : filter;
	}

}
