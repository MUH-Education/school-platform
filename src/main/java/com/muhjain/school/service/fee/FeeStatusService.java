package com.muhjain.school.service.fee;

import com.muhjain.school.model.dto.response.FamilyDues;
import com.muhjain.school.model.entity.FeeMonthView;
import com.muhjain.school.model.entity.SessionFeeSetting;
import com.muhjain.school.model.enums.FeeStatus;
import com.muhjain.school.model.enums.ReceiptKind;
import com.muhjain.school.model.enums.ReceiptStatus;
import com.muhjain.school.model.repository.FamilyCreditEntryRepository;
import com.muhjain.school.model.repository.FeeMonthViewRepository;
import com.muhjain.school.model.repository.ReceiptRepository;
import com.muhjain.school.model.repository.SessionFeeSettingRepository;
import com.muhjain.school.service.setting.SettingService;
import com.muhjain.school.util.FamilyDuesCalculator;
import com.muhjain.school.util.FeeStatusCalculator;
import com.muhjain.school.util.FeeStatusCalculator.MonthInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The fee status of many children at once, for lists (the "Fee" column of the Students screen, and later
 * Analytics). The maths is in {@link FeeStatusCalculator}, reading {@code v_fee_month}.
 * Example: {118 → DELAYED, 119 → ON_TIME}. A child with no fee due rows this session is not in the map.
 */
@Service
public class FeeStatusService {

	private final FeeMonthViewRepository months;

	private final AcademicSessionService sessions;

	private final SettingService settings;

	private final SessionFeeSettingRepository feeSettings;

	private final FamilyCreditEntryRepository credits;

	private final ReceiptRepository receipts;

	private final Clock clock;

	public FeeStatusService(FeeMonthViewRepository months, AcademicSessionService sessions, SettingService settings,
			SessionFeeSettingRepository feeSettings, FamilyCreditEntryRepository credits, ReceiptRepository receipts,
			Clock clock) {
		this.months = months;
		this.sessions = sessions;
		this.settings = settings;
		this.feeSettings = feeSettings;
		this.credits = credits;
		this.receipts = receipts;
		this.clock = clock;
	}

	/** Status of each child that has dues in the current session, as of today. */
	@Transactional(readOnly = true)
	public Map<Long, FeeStatus> statuses(Collection<Long> studentIds) {
		Map<Long, FeeStatusCalculator.Summary> found = summaries(sessions.current().getId(), studentIds);
		Map<Long, FeeStatus> result = new HashMap<>();
		found.forEach((studentId, summary) -> result.put(studentId, summary.status()));
		return result;
	}

	/**
	 * What each family owes right now (current session, as of today), with the late fee if it paid today. One
	 * query for all families. Notifications use it for fee reminders, at draft time and again right before
	 * sending. Example: [[118, 119], [140]] → [FamilyDues(8500, [Sep, Oct], 1, 100), FamilyDues(9600, ...)].
	 *
	 * @param families the ACTIVE children of each family; the answer has the same order
	 */
	@Transactional(readOnly = true)
	public List<FamilyDues> familyDues(List<? extends Collection<Long>> families) {
		List<Long> all = families.stream().flatMap(Collection::stream).distinct().toList();
		if (all.isEmpty()) {
			return families.stream().map(f -> FamilyDues.NONE).toList();
		}
		Long sessionId = sessions.current().getId();
		Map<Long, FeeStatusCalculator.Summary> summaries = summaries(sessionId, all);
		FamilyDuesCalculator.LateFeeRule rule = feeSettings.findBySessionId(sessionId)
			.map(FeeStatusService::ruleOf)
			.orElse(FamilyDuesCalculator.LateFeeRule.NONE);
		LocalDate today = LocalDate.now(clock);
		List<FamilyDues> result = new ArrayList<>();
		for (Collection<Long> family : families) {
			List<FeeStatusCalculator.Summary> children = family.stream()
				.map(summaries::get)
				.filter(java.util.Objects::nonNull)
				.toList();
			result.add(FamilyDuesCalculator.of(children, rule, today));
		}
		return result;
	}

	/** {@link #familyDues(List)} of one family. */
	@Transactional(readOnly = true)
	public FamilyDues familyDues(Collection<Long> studentIds) {
		return familyDues(List.of(studentIds)).getFirst();
	}

	/** The family's credit balance (money kept for later). Example: guardian 210 → 1500.00. */
	@Transactional(readOnly = true)
	public BigDecimal familyCredit(Long guardianId) {
		return credits.balanceOf(guardianId);
	}

	/** Did this family (its primary guardian) pay something on this day? A cancelled receipt does not count. */
	@Transactional(readOnly = true)
	public boolean paidOn(Long guardianId, LocalDate day) {
		return receipts.existsByFamilyGuardianIdAndPaidOnAndKindAndStatus(guardianId, day, ReceiptKind.PAYMENT,
				ReceiptStatus.ACTIVE);
	}

	private static FamilyDuesCalculator.LateFeeRule ruleOf(SessionFeeSetting s) {
		return new FamilyDuesCalculator.LateFeeRule(s.getLateFeeKind(), s.getLateFeePerMonth(), s.getLateFeePerDay(),
				s.getLateFeeMaxPerMonth());
	}

	/**
	 * {@link #summaries} for the current session, as of today. Notifications use it for fee reminders: the numbers
	 * are read live at send time, never stored.
	 */
	@Transactional(readOnly = true)
	public Map<Long, FeeStatusCalculator.Summary> currentSummaries(Collection<Long> studentIds) {
		return summaries(sessions.current().getId(), studentIds);
	}

	/**
	 * The full fee numbers of each child that has dues in that session, as of today. Analytics uses it, so a
	 * list there shows exactly what the child's own page shows. Example: {118 → Summary(status DELAYED,
	 * pendingNow 7500)}.
	 */
	@Transactional(readOnly = true)
	public Map<Long, FeeStatusCalculator.Summary> summaries(Long sessionId, Collection<Long> studentIds) {
		if (studentIds.isEmpty()) {
			return Map.of();
		}
		LocalDate today = LocalDate.now(clock);
		int defaultedAfter = Integer.parseInt(settings.value("fees.defaulted_after_days"));
		Map<Long, List<FeeMonthView>> byStudent = months.findByStudentIdInAndSessionId(studentIds, sessionId)
			.stream()
			.collect(Collectors.groupingBy(FeeMonthView::getStudentId));
		Map<Long, FeeStatusCalculator.Summary> result = new HashMap<>();
		byStudent.forEach((studentId, rows) -> result.put(studentId,
				FeeStatusCalculator.calculate(rows.stream().map(FeeStatusService::toInput).toList(), today,
						defaultedAfter)));
		return result;
	}

	static MonthInput toInput(FeeMonthView v) {
		return new MonthInput(v.getFeeDueId(), v.getMonth(), v.getSchoolAmount(), v.getDiscountAmount(),
				v.getBusAmount(), v.getTotal(), v.getPaidSchool(), v.getPaidBus(), v.getPaidLate(), v.getBalance(),
				v.getDueDate(), v.getGraceDate());
	}

}
