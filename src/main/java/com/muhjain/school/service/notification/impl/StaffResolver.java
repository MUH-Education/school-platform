package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.StaffResponse;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.model.enums.StaffType;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.staff.StaffService;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * STAFF_ALL (every type, helpers too), DRIVERS, ATTENDANTS, TEACHERS: the employees who are turned on. Never
 * touched by the stop list. Values: {@code Name}, {@code Staff name}.
 * Example: DRIVERS → Jagdish, Rajpal, ... (9 drivers).
 */
@Component
public class StaffResolver implements RecipientResolver {

	private final StaffService staffService;

	public StaffResolver(StaffService staffService) {
		this.staffService = staffService;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.STAFF_ALL, AudienceGroup.DRIVERS, AudienceGroup.ATTENDANTS, AudienceGroup.TEACHERS);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		Set<StaffType> types = switch (group) {
			case DRIVERS -> EnumSet.of(StaffType.DRIVER);
			case ATTENDANTS -> EnumSet.of(StaffType.ATTENDANT);
			case TEACHERS -> EnumSet.of(StaffType.TEACHER);
			default -> EnumSet.noneOf(StaffType.class);   // STAFF_ALL: every type
		};
		return staffService.list(types).stream().filter(StaffResponse::active).map(s -> recipient(s, group)).toList();
	}

	/** One employee as a recipient. Also used by the route crew and one-staff resolvers. */
	public static Recipient recipient(Long staffId, String name, String phone, AudienceGroup group,
			Map<String, String> extra) {
		Map<String, String> values = new java.util.LinkedHashMap<>();
		values.put("Name", (name == null) ? "" : name);
		values.put("Staff name", (name == null) ? "" : name);
		values.putAll(extra);
		return new Recipient(RecipientType.STAFF, staffId, phone, MessageChannel.WHATSAPP, group, List.of(), values);
	}

	private static Recipient recipient(StaffResponse s, AudienceGroup group) {
		return recipient(s.id(), s.name(), s.phone(), group, Map.of());
	}

}
