package com.muhjain.school.student;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A child's bus history. One row = one route and one stop, from a day to a day. {@code toDate} null means "still
 * going on". A child with no row uses no bus. A child has one open row only (a database index checks it).
 * Example: Ishaan, Route 9, Model Town stop, from 2 Nov 2026.
 * "On the bus on day D" means {@code fromDate <= D and (toDate is null or toDate >= D)}.
 */
@Entity
@Table(name = "transport_enrolment")
public class TransportEnrolment extends BaseEntity {

	@Column(name = "student_id", nullable = false, updatable = false)
	private Long studentId;

	@Column(name = "route_id", nullable = false, updatable = false)
	private Long routeId;

	@Column(name = "stop_id", nullable = false, updatable = false)
	private Long stopId;

	// The first day on this route.
	@Column(name = "from_date", nullable = false, updatable = false)
	private LocalDate fromDate;

	// The last day. Null = still going on.
	@Column(name = "to_date")
	private LocalDate toDate;

	// The fee agreed for this period. Null = not set.
	@Column(name = "bus_fee", precision = 12, scale = 2)
	private BigDecimal busFee;

	@Column(name = "created_by")
	private Long createdBy;

	protected TransportEnrolment() {
	}

	public TransportEnrolment(Long studentId, Long routeId, Long stopId, LocalDate fromDate, BigDecimal busFee,
			Long createdBy) {
		this.studentId = studentId;
		this.routeId = routeId;
		this.stopId = stopId;
		this.fromDate = fromDate;
		this.busFee = busFee;
		this.createdBy = createdBy;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getRouteId() {
		return routeId;
	}

	public Long getStopId() {
		return stopId;
	}

	public LocalDate getFromDate() {
		return fromDate;
	}

	public LocalDate getToDate() {
		return toDate;
	}

	public void setToDate(LocalDate toDate) {
		this.toDate = toDate;
	}

	public BigDecimal getBusFee() {
		return busFee;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	/** Is the child on this bus on that day? */
	public boolean covers(LocalDate day) {
		return !fromDate.isAfter(day) && (toDate == null || !toDate.isBefore(day));
	}

}
