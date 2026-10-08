package com.muhjain.school.analytics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.muhjain.school.fee.FeeHead;
import com.muhjain.school.fee.SessionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers behind the Analytics screen. Every method starts from {@link AnalyticsBase#students}, so all of them
 * count the same children for the same filter.
 */
@Service
public class AnalyticsService {

	private final AnalyticsBase base;

	private final Clock clock;

	public AnalyticsService(AnalyticsBase base, Clock clock) {
		this.base = base;
		this.clock = clock;
	}

	/** Rule 4. A filter that matches nobody gives zeros. */
	@Transactional(readOnly = true)
	public SummaryResponse summary(StudentFilter filter) {
		SessionResponse session = base.session(filter);
		List<AnalyticsStudent> students = base.students(filter);
		LocalDate today = LocalDate.now(clock);
		int onBus = (int) students.stream().filter(AnalyticsStudent::usesBus).count();
		int withPending = (int) students.stream().filter(s -> s.pendingNow().signum() > 0).count();
		return new SummaryResponse(session.id(), session.name(), students.size(), onBus,
				CollectionMath.percent(BigDecimal.valueOf(onBus), BigDecimal.valueOf(students.size())),
				CollectionMath.percent(collected(students, FeeHead.SCHOOL, today)),
				CollectionMath.percent(collected(students, FeeHead.BUS, today)), withPending);
	}

	private static CollectionMath.Totals collected(List<AnalyticsStudent> students, FeeHead head, LocalDate today) {
		CollectionMath.Totals total = CollectionMath.Totals.ZERO;
		for (AnalyticsStudent s : students) {
			if (s.fee() != null) {
				total = total.plus(CollectionMath.upTo(s.fee().dues(), head, today));
			}
		}
		return total;
	}

}
