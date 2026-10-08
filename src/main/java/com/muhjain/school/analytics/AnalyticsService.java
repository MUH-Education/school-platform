package com.muhjain.school.analytics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.muhjain.school.fee.FeeHead;
import com.muhjain.school.fee.FeeStatusCalculator.CoveredDue;
import com.muhjain.school.fee.FeeStatus;
import com.muhjain.school.fee.SessionResponse;
import com.muhjain.school.student.FatherOccupation;
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

	/** Rule 5. Months up to this month, each with school and bus. */
	@Transactional(readOnly = true)
	public MonthlyCollectionResponse feeCollectionByMonth(StudentFilter filter) {
		SessionResponse session = base.session(filter);
		List<CoveredDue> dues = base.students(filter)
			.stream()
			.filter(s -> s.fee() != null)
			.flatMap(s -> s.fee().dues().stream())
			.toList();
		return new MonthlyCollectionResponse(session.id(), session.name(), MonthlyCollectionCalculator.calculate(dues,
				session.startsOn(), session.endsOn(), LocalDate.now(clock)));
	}

	/** Rule 6. One row for each occupation, also those with nobody. */
	@Transactional(readOnly = true)
	public PaymentByOccupationResponse paymentByOccupation(StudentFilter filter) {
		SessionResponse session = base.session(filter);
		List<AnalyticsStudent> students = base.students(filter);
		List<OccupationRow> rows = new ArrayList<>();
		for (FatherOccupation occupation : FatherOccupation.values()) {
			List<AnalyticsStudent> of = students.stream().filter(s -> s.occupation() == occupation).toList();
			rows.add(new OccupationRow(occupation, countOf(of, FeeStatus.ON_TIME), countOf(of, FeeStatus.DELAYED),
					countOf(of, FeeStatus.DEFAULTED), countOf(of, null), of.size()));
		}
		return new PaymentByOccupationResponse(session.id(), session.name(), rows);
	}

	// status null = the children without a fee plan
	private static int countOf(List<AnalyticsStudent> students, FeeStatus status) {
		return (int) students.stream().filter(s -> s.feeStatus() == status).count();
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
