package com.muhjain.school.service.fee;

import com.muhjain.school.model.entity.FeeMonthView;
import com.muhjain.school.model.enums.FeeStatus;
import com.muhjain.school.model.repository.FeeMonthViewRepository;
import com.muhjain.school.service.setting.SettingService;
import com.muhjain.school.util.FeeStatusCalculator;
import com.muhjain.school.util.FeeStatusCalculator.MonthInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
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

	private final Clock clock;

	public FeeStatusService(FeeMonthViewRepository months, AcademicSessionService sessions, SettingService settings,
			Clock clock) {
		this.months = months;
		this.sessions = sessions;
		this.settings = settings;
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
