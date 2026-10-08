package com.muhjain.school.analytics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.muhjain.school.fee.FeeHead;
import com.muhjain.school.fee.FeeStatusCalculator.CoveredDue;
import com.muhjain.school.fee.FeeStatus;
import com.muhjain.school.fee.SessionResponse;
import com.muhjain.school.student.ClassNames;
import com.muhjain.school.student.FatherOccupation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers behind the Analytics screen. Every method starts from {@link AnalyticsBase#students}, so all of them
 * count the same children for the same filter.
 */
@Service
public class AnalyticsService {

	private static final int TOP_VILLAGES = 8;

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

	/** Rule 7. All 15 classes in school order, also those with 0. */
	@Transactional(readOnly = true)
	public StudentsByClassResponse studentsByClass(StudentFilter filter) {
		SessionResponse session = base.session(filter);
		List<AnalyticsStudent> students = base.students(filter);
		Map<String, Long> counts = students.stream()
			.collect(Collectors.groupingBy(AnalyticsStudent::className, Collectors.counting()));
		List<ClassCount> classes = ClassNames.ALL.stream()
			.map(c -> new ClassCount(c, counts.getOrDefault(c, 0L).intValue()))
			.toList();
		return new StudentsByClassResponse(session.id(), session.name(), students.size(), classes);
	}

	/**
	 * Rule 8. Villages are matched without caring for capital letters ("jakhal" and "Jakhal" are one village); the
	 * first spelling in name order is shown. Biggest first; the same size → by name.
	 */
	@Transactional(readOnly = true)
	public StudentsByVillageResponse studentsByVillage(StudentFilter filter) {
		SessionResponse session = base.session(filter);
		List<AnalyticsStudent> students = base.students(filter);
		Map<String, List<AnalyticsStudent>> byVillage = students.stream()
			.collect(Collectors.groupingBy(s -> s.village().strip().toLowerCase(Locale.ROOT)));
		List<VillageCount> all = byVillage.values()
			.stream()
			.map(list -> new VillageCount(list.get(0).village().strip(), list.size()))
			.sorted(Comparator.comparingInt(VillageCount::students)
				.reversed()
				.thenComparing(VillageCount::village, String.CASE_INSENSITIVE_ORDER))
			.toList();
		List<VillageCount> top = all.subList(0, Math.min(TOP_VILLAGES, all.size()));
		List<VillageCount> rest = all.subList(top.size(), all.size());
		return new StudentsByVillageResponse(session.id(), session.name(), students.size(), top,
				new StudentsByVillageResponse.Others(rest.size(), rest.stream().mapToInt(VillageCount::students).sum()));
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
