package com.muhjain.school.vehicle;

import java.math.BigDecimal;

/**
 * The few facts about a vehicle that other features need. Example: Van 4, SMALL_VAN, 14 seats, 30300.00 a month.
 * Routes and the load board use this, so they do not need the whole {@link VehicleResponse}.
 */
public record VehicleSummary(Long id, String name, VehicleType vehicleType, int seats, BigDecimal monthlyCost,
		boolean active) {

	static VehicleSummary of(Vehicle vehicle) {
		return new VehicleSummary(vehicle.getId(), vehicle.getName(), vehicle.getVehicleType(), vehicle.getSeats(),
				vehicle.getMonthlyCost(), vehicle.isActive());
	}

}
