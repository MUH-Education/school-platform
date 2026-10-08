package com.muhjain.school.analytics;

import com.muhjain.school.common.PageResponse;
import com.muhjain.school.fee.FeeStatus;
import com.muhjain.school.student.BusFilter;
import com.muhjain.school.student.FatherOccupation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Analytics screen. Every URL needs ANALYTICS_VIEW and takes the same filter in the query:
 * {@code sessionId}, {@code className} (one class or a group like 1-5), {@code village}, {@code routeId},
 * {@code bus=YES|NO}, {@code occupation}, {@code feeStatus}.
 * Example: {@code GET /api/v1/analytics/summary?village=Jakhal&feeStatus=DELAYED}
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

	private final AnalyticsService analytics;

	public AnalyticsController(AnalyticsService analytics) {
		this.analytics = analytics;
	}

	/** Students, on the bus, % of school and bus fee collected, children with something pending. */
	@GetMapping("/summary")
	@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
	public SummaryResponse summary(@RequestParam(required = false) Long sessionId,
			@RequestParam(required = false) String className, @RequestParam(required = false) String village,
			@RequestParam(required = false) Long routeId, @RequestParam(required = false) BusFilter bus,
			@RequestParam(required = false) FatherOccupation occupation,
			@RequestParam(required = false) FeeStatus feeStatus) {
		return analytics.summary(StudentFilter.of(sessionId, className, village, routeId, bus, occupation, feeStatus));
	}

	/** For each month up to this month: % of school fee and % of bus fee collected. */
	@GetMapping("/fee-collection-by-month")
	@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
	public MonthlyCollectionResponse feeCollectionByMonth(@RequestParam(required = false) Long sessionId,
			@RequestParam(required = false) String className, @RequestParam(required = false) String village,
			@RequestParam(required = false) Long routeId, @RequestParam(required = false) BusFilter bus,
			@RequestParam(required = false) FatherOccupation occupation,
			@RequestParam(required = false) FeeStatus feeStatus) {
		return analytics
			.feeCollectionByMonth(StudentFilter.of(sessionId, className, village, routeId, bus, occupation, feeStatus));
	}

	/** For each father's occupation: how many children are ON_TIME, DELAYED, DEFAULTED (and how many have no plan). */
	@GetMapping("/payment-by-occupation")
	@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
	public PaymentByOccupationResponse paymentByOccupation(@RequestParam(required = false) Long sessionId,
			@RequestParam(required = false) String className, @RequestParam(required = false) String village,
			@RequestParam(required = false) Long routeId, @RequestParam(required = false) BusFilter bus,
			@RequestParam(required = false) FatherOccupation occupation,
			@RequestParam(required = false) FeeStatus feeStatus) {
		return analytics
			.paymentByOccupation(StudentFilter.of(sessionId, className, village, routeId, bus, occupation, feeStatus));
	}

	/** Children in each of the 15 classes, in school order, also classes with 0. */
	@GetMapping("/students-by-class")
	@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
	public StudentsByClassResponse studentsByClass(@RequestParam(required = false) Long sessionId,
			@RequestParam(required = false) String className, @RequestParam(required = false) String village,
			@RequestParam(required = false) Long routeId, @RequestParam(required = false) BusFilter bus,
			@RequestParam(required = false) FatherOccupation occupation,
			@RequestParam(required = false) FeeStatus feeStatus) {
		return analytics
			.studentsByClass(StudentFilter.of(sessionId, className, village, routeId, bus, occupation, feeStatus));
	}

	/** Children per village, biggest first: the top 8 and then "others". */
	@GetMapping("/students-by-village")
	@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
	public StudentsByVillageResponse studentsByVillage(@RequestParam(required = false) Long sessionId,
			@RequestParam(required = false) String className, @RequestParam(required = false) String village,
			@RequestParam(required = false) Long routeId, @RequestParam(required = false) BusFilter bus,
			@RequestParam(required = false) FatherOccupation occupation,
			@RequestParam(required = false) FeeStatus feeStatus) {
		return analytics
			.studentsByVillage(StudentFilter.of(sessionId, className, village, routeId, bus, occupation, feeStatus));
	}

	/**
	 * The students behind the graphs, paged: {@code ?page=0&size=25&sort=pendingAmount,desc}. Sort by {@code name},
	 * {@code className} or {@code pendingAmount}.
	 */
	@GetMapping("/students")
	@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
	public PageResponse<AnalyticsStudentItem> students(@RequestParam(required = false) Long sessionId,
			@RequestParam(required = false) String className, @RequestParam(required = false) String village,
			@RequestParam(required = false) Long routeId, @RequestParam(required = false) BusFilter bus,
			@RequestParam(required = false) FatherOccupation occupation,
			@RequestParam(required = false) FeeStatus feeStatus, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		return analytics.students(StudentFilter.of(sessionId, className, village, routeId, bus, occupation, feeStatus),
				page, size, sort);
	}

	/**
	 * The same list as a file for Excel: all matching rows (not one page), header first, UTF-8 with BOM. Every
	 * download is written to the audit log.
	 */
	@GetMapping(value = "/students.csv", produces = "text/csv;charset=UTF-8")
	@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
	public ResponseEntity<byte[]> studentsCsv(@RequestParam(required = false) Long sessionId,
			@RequestParam(required = false) String className, @RequestParam(required = false) String village,
			@RequestParam(required = false) Long routeId, @RequestParam(required = false) BusFilter bus,
			@RequestParam(required = false) FatherOccupation occupation,
			@RequestParam(required = false) FeeStatus feeStatus, @RequestParam(required = false) String sort) {
		CsvFile file = analytics.csv(StudentFilter.of(sessionId, className, village, routeId, bus, occupation, feeStatus),
				sort);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
			.body(file.content());
	}

}
