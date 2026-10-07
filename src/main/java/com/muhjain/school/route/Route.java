package com.muhjain.school.route;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

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

}
