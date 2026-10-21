package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.TransportReason;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A child's bus history, as periods that never overlap. Every change is a new row, and the row before it
 * gets a {@code toDate}. {@code usesBus = false} is itself a period ("no bus" for these days), not the
 * absence of a row. {@code toDate} null means "still going on".
 * Example: Ishaan, no bus from 1 Apr 2026 (row 1). Route 9 from 2 Nov (row 2). Row 1 gets toDate 1 Nov.
 * "On the bus on day D" means {@code fromDate <= D and (toDate is null or toDate >= D)}.
 */
@Entity
@Table(name = "transport_enrolment")
public class TransportEnrolment extends BaseEntity {

	@Column(name = "student_id", nullable = false, updatable = false)
	private Long studentId;

	@Column(name = "uses_bus", nullable = false, updatable = false)
	private boolean usesBus;

	// Null when usesBus is false.
	@Column(name = "route_id", updatable = false)
	private Long routeId;

	@Column(name = "stop_id", updatable = false)
	private Long stopId;

	// The first day on this period.
	@Column(name = "from_date", nullable = false, updatable = false)
	private LocalDate fromDate;

	// The last day. Null = still going on.
	@Column(name = "to_date")
	private LocalDate toDate;

	// The fee agreed for this period. Null when usesBus is false.
	@Column(name = "bus_fee_per_month", precision = 12, scale = 2)
	private BigDecimal busFeePerMonth;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private TransportReason reason;

	@Column(length = 200)
	private String note;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	protected TransportEnrolment() {
	}

	public TransportEnrolment(Long studentId, boolean usesBus, Long routeId, Long stopId, LocalDate fromDate,
			BigDecimal busFeePerMonth, TransportReason reason, String note, Long createdBy) {
		this.studentId = studentId;
		this.usesBus = usesBus;
		this.routeId = routeId;
		this.stopId = stopId;
		this.fromDate = fromDate;
		this.busFeePerMonth = busFeePerMonth;
		this.reason = reason;
		this.note = note;
		this.createdBy = createdBy;
	}

	public Long getStudentId() {
		return studentId;
	}

	public boolean isUsesBus() {
		return usesBus;
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

	public BigDecimal getBusFeePerMonth() {
		return busFeePerMonth;
	}

	public TransportReason getReason() {
		return reason;
	}

	public String getNote() {
		return note;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	/** Is the child on this bus on that day? */
	public boolean covers(LocalDate day) {
		return !fromDate.isAfter(day) && (toDate == null || !toDate.isBefore(day));
	}

}
