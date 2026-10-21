package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.FamilyDues;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.fee.FeeStatusService;
import com.muhjain.school.service.notification.FamilyRecipients;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.student.StudentQueryService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * FAMILIES_WITH_DUES: families of ACTIVE children with money pending right now (this session, as of today), read
 * live with {@link FeeStatusService#familyDues}, never from the draft. One message per family; its children are
 * added up. Filters: classes, {@code minMonthsLate}, {@code minAmount} (on the family total).
 * Values: {@link FeeDueValues}. The skip rules (paid since, promised, reminded recently) are the guard chain's job;
 * {@code FeeDueGuard} reads the same numbers again at send time.
 */
@Component
public class FamiliesWithDuesResolver implements RecipientResolver {

	private final StudentQueryService studentQuery;

	private final FeeStatusService feeStatusService;

	private final FamilyRecipients families;

	private final Clock clock;

	public FamiliesWithDuesResolver(StudentQueryService studentQuery, FeeStatusService feeStatusService,
			FamilyRecipients families, Clock clock) {
		this.studentQuery = studentQuery;
		this.feeStatusService = feeStatusService;
		this.families = families;
		this.clock = clock;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.FAMILIES_WITH_DUES);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		List<FamilyRecipients.Child> children = studentQuery
			.activeMatching(filter.classes().isEmpty() ? null : filter.classes(), null, null, null, null,
					LocalDate.now(clock))
			.stream()
			.map(c -> new FamilyRecipients.Child(c.id(), c.name(), c.className()))
			.toList();
		List<Recipient> all = families.of(children, group);
		List<FamilyDues> dues = feeStatusService.familyDues(all.stream().map(Recipient::studentIds).toList());
		List<Recipient> result = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			FamilyDues d = dues.get(i);
			if (d.amount().signum() > 0 && FeeDueValues.passes(d, filter)) {
				result.add(all.get(i).withValues(FeeDueValues.of(d)));
			}
		}
		return result;
	}

}
