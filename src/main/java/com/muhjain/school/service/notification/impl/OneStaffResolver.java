package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.StaffResponse;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.staff.StaffService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ONE_STAFF: the employees picked by id ({@code staffIds}), if they are turned on. An unknown id is left out.
 * Example: staffIds [31] → Sunita.
 */
@Component
public class OneStaffResolver implements RecipientResolver {

	private final StaffService staffService;

	public OneStaffResolver(StaffService staffService) {
		this.staffService = staffService;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.ONE_STAFF);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		if (filter.staffIds().isEmpty()) {
			return List.of();
		}
		// The whole staff list is small (about 50 people), so one read is simpler than one per id.
		return staffService.list(Set.of())
			.stream()
			.filter(s -> s.active() && filter.staffIds().contains(s.id()))
			.map(s -> StaffResolver.recipient(s.id(), s.name(), s.phone(), group, Map.of()))
			.toList();
	}

}
