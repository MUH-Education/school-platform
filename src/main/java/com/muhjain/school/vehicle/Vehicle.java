package com.muhjain.school.vehicle;

import java.math.BigDecimal;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One vehicle. Example: "Van 4", "HR 23 A 1104", SMALL_VAN, 14 seats, 30300.00 a month.
 * People and routes are not here. They point at the vehicle by id (see VehicleAssignment and Route).
 */
@Entity
@Table(name = "vehicle")
public class Vehicle extends BaseEntity {

	@Column(nullable = false, length = 40)
	private String name;

	@Column(name = "registration_no", nullable = false, length = 20)
	private String registrationNo;

	@Enumerated(EnumType.STRING)
	@Column(name = "vehicle_type", nullable = false, length = 20)
	private VehicleType vehicleType;

	@Column(nullable = false)
	private int seats;

	// All-in cost of one month. Money is BigDecimal, never double.
	@Column(name = "monthly_cost", nullable = false, precision = 12, scale = 2)
	private BigDecimal monthlyCost;

	@Enumerated(EnumType.STRING)
	@Column(name = "owned_by", nullable = false, length = 20)
	private OwnedBy ownedBy;

	@Column(nullable = false)
	private boolean active = true;

	protected Vehicle() {
	}

	public Vehicle(String name, String registrationNo, VehicleType vehicleType, int seats, BigDecimal monthlyCost,
			OwnedBy ownedBy) {
		this.name = name;
		this.registrationNo = registrationNo;
		this.vehicleType = vehicleType;
		this.seats = seats;
		this.monthlyCost = monthlyCost;
		this.ownedBy = ownedBy;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRegistrationNo() {
		return registrationNo;
	}

	public void setRegistrationNo(String registrationNo) {
		this.registrationNo = registrationNo;
	}

	public VehicleType getVehicleType() {
		return vehicleType;
	}

	public void setVehicleType(VehicleType vehicleType) {
		this.vehicleType = vehicleType;
	}

	public int getSeats() {
		return seats;
	}

	public void setSeats(int seats) {
		this.seats = seats;
	}

	public BigDecimal getMonthlyCost() {
		return monthlyCost;
	}

	public void setMonthlyCost(BigDecimal monthlyCost) {
		this.monthlyCost = monthlyCost;
	}

	public OwnedBy getOwnedBy() {
		return ownedBy;
	}

	public void setOwnedBy(OwnedBy ownedBy) {
		this.ownedBy = ownedBy;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
