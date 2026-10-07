package com.muhjain.school.staff;

import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Who works on which vehicle, and when. One row = one person, one vehicle, one duty, from a day to a day.
 * {@code toDate} null means "still going on". A temporary row is a replacement for some days.
 * Example: Surender, Van 4, DRIVER, 12 Oct to 16 Oct, temporary, ON_LEAVE (Jagdish is on leave).
 * The vehicle and the person are ids, not objects (docs/02-architecture.md).
 */
@Entity
@Table(name = "vehicle_assignment")
public class VehicleAssignment extends BaseEntity {

	@Column(name = "vehicle_id", nullable = false)
	private Long vehicleId;

	@Column(name = "staff_id", nullable = false)
	private Long staffId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Duty duty;

	// The first day.
	@Column(name = "from_date", nullable = false)
	private LocalDate fromDate;

	// The last day. Null = still going on.
	@Column(name = "to_date")
	private LocalDate toDate;

	@Column(nullable = false)
	private boolean temporary;

	@Enumerated(EnumType.STRING)
	@Column(length = 40)
	private ChangeReason reason;

	@Column(name = "created_by")
	private Long createdBy;

	protected VehicleAssignment() {
	}

	public VehicleAssignment(Long vehicleId, Long staffId, Duty duty, LocalDate fromDate, LocalDate toDate,
			boolean temporary, ChangeReason reason, Long createdBy) {
		this.vehicleId = vehicleId;
		this.staffId = staffId;
		this.duty = duty;
		this.fromDate = fromDate;
		this.toDate = toDate;
		this.temporary = temporary;
		this.reason = reason;
		this.createdBy = createdBy;
	}

	/** Does this row cover the day? Example: 12 to 16 Oct covers 14 Oct, not 17 Oct. */
	public boolean covers(LocalDate day) {
		return !fromDate.isAfter(day) && (toDate == null || !toDate.isBefore(day));
	}

	public Long getVehicleId() {
		return vehicleId;
	}

	public Long getStaffId() {
		return staffId;
	}

	public Duty getDuty() {
		return duty;
	}

	public LocalDate getFromDate() {
		return fromDate;
	}

	public LocalDate getToDate() {
		return toDate;
	}

	/** Used to close a permanent row: {@code to_date = fromDate - 1}. */
	public void setToDate(LocalDate toDate) {
		this.toDate = toDate;
	}

	public boolean isTemporary() {
		return temporary;
	}

	public ChangeReason getReason() {
		return reason;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}
