package com.muhjain.school.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalTime;

/**
 * A route is run by one vehicle and has ordered stops. Example: "Route 4" runs on Van 4.
 * The vehicle is an id, not an object (docs/02-architecture.md). A route may have no vehicle yet.
 */
@Entity
@Table(name = "route")
public class Route extends BaseEntity {

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "vehicle_id")
	private Long vehicleId;

	@Column(nullable = false)
	private boolean active = true;

	// When the route is due at school: morning arrival, and evening leave to start the drop-off.
	@Column(name = "school_arrival_time", nullable = false)
	private LocalTime schoolArrivalTime = LocalTime.of(8, 10);

	@Column(name = "school_leave_time", nullable = false)
	private LocalTime schoolLeaveTime = LocalTime.of(14, 40);

	protected Route() {
	}

	public Route(String name, Long vehicleId) {
		this.name = name;
		this.vehicleId = vehicleId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getVehicleId() {
		return vehicleId;
	}

	public void setVehicleId(Long vehicleId) {
		this.vehicleId = vehicleId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public LocalTime getSchoolArrivalTime() {
		return schoolArrivalTime;
	}

	public void setSchoolArrivalTime(LocalTime schoolArrivalTime) {
		this.schoolArrivalTime = schoolArrivalTime;
	}

	public LocalTime getSchoolLeaveTime() {
		return schoolLeaveTime;
	}

	public void setSchoolLeaveTime(LocalTime schoolLeaveTime) {
		this.schoolLeaveTime = schoolLeaveTime;
	}

}
