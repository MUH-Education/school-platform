package com.muhjain.school.analytics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.auth.CurrentUser;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.PageResponse;
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

	private static final int MAX_PAGE_SIZE = 100;

	static final String AUDIT_ENTITY = "ANALYTICS_CSV";

	private final AnalyticsBase base;

	private final AuditService audit;

	private final CurrentUser currentUser;

	private final Clock clock;

	public AnalyticsService(AnalyticsBase base, AuditService audit, CurrentUser currentUser, Clock clock) {
		this.base = base;
		this.audit = audit;
		this.currentUser = currentUser;
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

	/**
	 * Rule 9. One page of the list.
	 *
	 * @param sort {@code name}, {@code className} (school order, Nursery first) or {@code pendingAmount}, then
	 * {@code ,asc} or {@code ,desc}. Default {@code name,asc}.
	 * @throws com.muhjain.school.common.ApiException 400 VALIDATION for page, size or sort
	 */
	@Transactional(readOnly = true)
	public PageResponse<AnalyticsStudentItem> students(StudentFilter filter, int page, int size, String sort) {
		if (page < 0) {
			throw ApiException.validation("page", "must be 0 or more");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw ApiException.validation("size", "must be between 1 and " + MAX_PAGE_SIZE);
		}
		Comparator<AnalyticsStudentItem> order = order(sort);
		List<AnalyticsStudentItem> all = rows(filter, order);
		int from = (int) Math.min((long) page * size, all.size());
		List<AnalyticsStudentItem> items = all.subList(from, Math.min(from + size, all.size()));
		return new PageResponse<>(items, page, size, all.size(), (all.size() + size - 1) / size);
	}

	/** Every matching child, not one page. The CSV file uses it. */
	@Transactional(readOnly = true)
	public List<AnalyticsStudentItem> allStudents(StudentFilter filter, String sort) {
		return rows(filter, order(sort));
	}

	/**
	 * Rules 10 and 11. The same list as {@link #students} but all rows, as a CSV file. Each download writes one
	 * {@code audit_log} row in the same transaction, so a file never leaves without a record.
	 * <p>
	 * {@code audit_log.action} knows only CREATED, UPDATED, DELETED and LOGIN and there is no migration in this phase,
	 * so the row is {@code ANALYTICS_CSV / CREATED}, its entity id is the user who downloaded, and {@code details}
	 * holds the filters. Example: "Students CSV downloaded: 12 rows" by Rishabh, {@code {"village":"Jakhal"}}.
	 */
	@Transactional
	public CsvFile csv(StudentFilter filter, String sort) {
		SessionResponse session = base.session(filter);
		List<AnalyticsStudentItem> rows = rows(filter, order(sort));
		Long userId = currentUser.id();
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("sessionId", session.id());
		put(details, "className", (filter.classNames() == null) ? null : String.join(",", filter.classNames()));
		put(details, "village", filter.village());
		put(details, "routeId", filter.routeId());
		put(details, "bus", filter.bus());
		put(details, "occupation", filter.occupation());
		put(details, "feeStatus", filter.feeStatus());
		details.put("rows", rows.size());
		audit.record(AUDIT_ENTITY, userId, AuditAction.CREATED,
				"Students CSV downloaded: " + rows.size() + " rows, school year " + session.name(), details);
		return new CsvFile("students-" + LocalDate.now(clock) + ".csv", AnalyticsCsv.write(rows));
	}

	private static void put(Map<String, Object> details, String key, Object value) {
		if (value != null) {
			details.put(key, (value instanceof Enum<?> e) ? e.name() : value);
		}
	}

	private List<AnalyticsStudentItem> rows(StudentFilter filter, Comparator<AnalyticsStudentItem> order) {
		return base.students(filter)
			.stream()
			.map(s -> new AnalyticsStudentItem(s.id(), s.name(), s.className(), s.village(), s.occupation(),
					s.routeName(), s.headStatus(FeeHead.SCHOOL), s.headStatus(FeeHead.BUS), s.feeStatus(),
					s.pendingNow()))
			.sorted(order.thenComparing(AnalyticsStudentItem::id))
			.toList();
	}

	private static Comparator<AnalyticsStudentItem> order(String sort) {
		String property = "name";
		boolean descending = false;
		if (sort != null && !sort.isBlank()) {
			String[] parts = sort.split(",");
			property = parts[0].strip();
			descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].strip());
		}
		Comparator<AnalyticsStudentItem> order = switch (property) {
			case "name" -> Comparator.comparing(AnalyticsStudentItem::name, String.CASE_INSENSITIVE_ORDER);
			case "className" -> Comparator.comparingInt(i -> ClassNames.ALL.indexOf(i.className()));
			case "pendingAmount" -> Comparator.comparing(AnalyticsStudentItem::pendingAmount);
			default -> throw ApiException.validation("sort", "can be one of name, className, pendingAmount");
		};
		return descending ? order.reversed() : order;
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
