package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.muhjain.school.fee.FeeStatusCalculator.DueInput;
import com.muhjain.school.setting.SettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fee status of many children at once, for lists (the "Fee" column of the Students screen, and later
 * Analytics). Three queries for any number of children. The maths is in {@link FeeStatusCalculator}.
 * Example: {118 → DELAYED, 119 → ON_TIME}. A child with no plan this session is not in the map.
 */
@Service
public class FeeStatusService {

	private final FeePlanRepository plans;

	private final FeeDueRepository dues;

	private final FeePaymentRepository payments;

	private final SessionService sessions;

	private final SettingService settings;

	private final Clock clock;

	public FeeStatusService(FeePlanRepository plans, FeeDueRepository dues, FeePaymentRepository payments,
			SessionService sessions, SettingService settings, Clock clock) {
		this.plans = plans;
		this.dues = dues;
		this.payments = payments;
		this.sessions = sessions;
		this.settings = settings;
		this.clock = clock;
	}

	/** Status of each child that has a plan in the current session, as of today. */
	@Transactional(readOnly = true)
	public Map<Long, FeeStatus> statuses(Collection<Long> studentIds) {
		if (studentIds.isEmpty()) {
			return Map.of();
		}
		LocalDate today = LocalDate.now(clock);
		AcademicSession session = sessions.current();
		List<FeePlan> found = plans.findBySessionIdAndStudentIdIn(session.getId(), studentIds);
		if (found.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<DueInput>> duesByPlan = dues
			.findByFeePlanIdIn(found.stream().map(FeePlan::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(FeeDue::getFeePlanId,
					Collectors.mapping(d -> new DueInput(d.getId(), d.getFeeHead(), d.getDueOn(), d.getAmount()),
							Collectors.toList())));
		Map<Long, Map<FeeHead, BigDecimal>> paidByStudent = new HashMap<>();
		for (Object[] row : payments.paidByStudentAndHead(session.getId(), found.stream().map(FeePlan::getStudentId).toList())) {
			paidByStudent.computeIfAbsent((Long) row[0], k -> new EnumMap<>(FeeHead.class))
				.put((FeeHead) row[1], (BigDecimal) row[2]);
		}
		int grace = Integer.parseInt(settings.value("fees.grace_days"));
		int defaultedAfter = Integer.parseInt(settings.value("fees.defaulted_after_days"));
		Map<Long, FeeStatus> result = new HashMap<>();
		for (FeePlan plan : found) {
			FeeStatusCalculator.Summary summary = FeeStatusCalculator.calculate(
					duesByPlan.getOrDefault(plan.getId(), List.of()),
					paidByStudent.getOrDefault(plan.getStudentId(), Map.of()), today, grace, defaultedAfter);
			result.put(plan.getStudentId(), summary.status());
		}
		return result;
	}

}
