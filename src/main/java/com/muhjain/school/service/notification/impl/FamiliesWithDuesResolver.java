package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.StudentReportRow;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.fee.FeeStatusService;
import com.muhjain.school.service.notification.FamilyRecipients;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.student.StudentQueryService;
import com.muhjain.school.util.FeeStatusCalculator;
import com.muhjain.school.util.MoneyText;
import com.muhjain.school.util.RecipientText;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * FAMILIES_WITH_DUES: families of ACTIVE children with money pending right now (this session, as of today), read
 * live from the fee numbers, never from the draft. One message per family; the numbers of all its children are
 * added up. Filters: classes, {@code minMonthsLate} (months past their grace day and not paid), {@code minAmount}.
 * Values: {@code Amount due} ("₹7,500"), {@code Months} ("सितंबर और अक्टूबर"), {@code Months late} ("2").
 * The late fee and the skip rules (paid since, promised, reminded recently) are the guard chain's job (task 11.6b).
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

	/** What one child owes now. */
	private record Owed(BigDecimal amount, Set<LocalDate> dueMonths, Set<LocalDate> lateMonths) {
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.FAMILIES_WITH_DUES);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		List<StudentReportRow> children = studentQuery.activeMatching(
				filter.classes().isEmpty() ? null : filter.classes(), null, null, null, null, LocalDate.now(clock));
		if (children.isEmpty()) {
			return List.of();
		}
		Map<Long, FeeStatusCalculator.Summary> summaries = feeStatusService
			.currentSummaries(children.stream().map(StudentReportRow::id).toList());
		Map<Long, Owed> owed = new HashMap<>();
		summaries.forEach((studentId, summary) -> {
			if (summary.pendingNow().signum() > 0) {
				owed.put(studentId, owedOf(summary));
			}
		});
		List<FamilyRecipients.Child> withDues = children.stream()
			.filter(c -> owed.containsKey(c.id()))
			.map(c -> new FamilyRecipients.Child(c.id(), c.name(), c.className()))
			.toList();
		return families.of(withDues, group, family -> values(family, owed))
			.stream()
			.filter(r -> passes(r, filter, owed))
			.toList();
	}

	private static Owed owedOf(FeeStatusCalculator.Summary summary) {
		Set<LocalDate> due = new TreeSet<>();
		Set<LocalDate> late = new TreeSet<>();
		for (FeeStatusCalculator.MonthView month : summary.months()) {
			if (month.balance().signum() > 0 && month.due()) {
				due.add(month.month());
				if (month.late()) {
					late.add(month.month());
				}
			}
		}
		return new Owed(summary.pendingNow(), due, late);
	}

	// What a whole family owes: the numbers of its children added up.
	private static Owed familyOwed(List<Long> studentIds, Map<Long, Owed> owed) {
		BigDecimal amount = BigDecimal.ZERO;
		Set<LocalDate> due = new TreeSet<>();
		Set<LocalDate> late = new TreeSet<>();
		for (Long studentId : studentIds) {
			Owed o = owed.get(studentId);
			amount = amount.add(o.amount());
			due.addAll(o.dueMonths());
			late.addAll(o.lateMonths());
		}
		return new Owed(amount, due, late);
	}

	private static Map<String, String> values(List<FamilyRecipients.Child> family, Map<Long, Owed> owed) {
		Owed total = familyOwed(family.stream().map(FamilyRecipients.Child::studentId).toList(), owed);
		Map<String, String> values = new LinkedHashMap<>();
		values.put("Amount due", MoneyText.of(total.amount()));
		values.put("Months", RecipientText.hindiMonths(List.copyOf(total.dueMonths())));
		values.put("Months late", String.valueOf(total.lateMonths().size()));
		return values;
	}

	// The amount and month filters are on the family total, as the parent sees it.
	private static boolean passes(Recipient r, AudienceFilter filter, Map<Long, Owed> owed) {
		Owed total = familyOwed(r.studentIds(), owed);
		if (filter.minMonthsLate() != null && total.lateMonths().size() < filter.minMonthsLate()) {
			return false;
		}
		return filter.minAmount() == null || total.amount().compareTo(filter.minAmount()) >= 0;
	}

}
