package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.StudentReportRow;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.FeeStatus;
import com.muhjain.school.service.fee.FeeStatusService;
import com.muhjain.school.service.notification.FamilyRecipients;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.student.StudentQueryService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CURRENT_PARENTS: the families of the ACTIVE children, one message per family. Filters: classes, villages
 * (capital letters do not matter), routes (on that bus today), fee status (of the child, this session).
 * Example: classes ["10"] → the 17 families of Class 10.
 */
@Component
public class CurrentParentsResolver implements RecipientResolver {

	private final StudentQueryService studentQuery;

	private final FeeStatusService feeStatusService;

	private final FamilyRecipients families;

	private final Clock clock;

	public CurrentParentsResolver(StudentQueryService studentQuery, FeeStatusService feeStatusService,
			FamilyRecipients families, Clock clock) {
		this.studentQuery = studentQuery;
		this.feeStatusService = feeStatusService;
		this.families = families;
		this.clock = clock;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.CURRENT_PARENTS);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		LocalDate today = LocalDate.now(clock);
		List<StudentReportRow> children = studentQuery
			.activeMatching(filter.classes().isEmpty() ? null : filter.classes(), null, null, null, null, today)
			.stream()
			.filter(c -> filter.villages().isEmpty() || inVillages(c.village(), filter.villages()))
			// A child with no bus has no route; List.contains(null) would throw.
			.filter(c -> filter.routeIds().isEmpty() || (c.routeId() != null && filter.routeIds().contains(c.routeId())))
			.toList();
		if (filter.feeStatus() != null && !children.isEmpty()) {
			Map<Long, FeeStatus> statuses = feeStatusService.statuses(children.stream().map(StudentReportRow::id).toList());
			children = children.stream().filter(c -> statuses.get(c.id()) == filter.feeStatus()).toList();
		}
		return families.of(children.stream()
			.map(c -> new FamilyRecipients.Child(c.id(), c.name(), c.className()))
			.toList(), group);
	}

	private static boolean inVillages(String village, List<String> wanted) {
		Set<String> keys = wanted.stream().map(v -> v.strip().toLowerCase()).collect(Collectors.toSet());
		return village != null && keys.contains(village.strip().toLowerCase());
	}

}
