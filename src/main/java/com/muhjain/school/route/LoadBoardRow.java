package com.muhjain.school.route;

import java.math.BigDecimal;

import com.muhjain.school.vehicle.VehicleSummary;
import com.muhjain.school.vehicle.VehicleType;

/**
 * One row of the Routes and load screen. See {@link LoadBoardCalculator.RouteLoad} for what each number means.
 * Example: {@code { "routeId": 4, "name": "Route 4", "vehicle": "Van 4", "vehicleType": "SMALL_VAN", "seats": 14,
 * "children": 19, "load": 1.36, "overBy": 5, "spare": 0, "yearlyCost": 333300.00, "costPerChild": 17542.11,
 * "feeGot": 158840.00, "surplus": -174460.00, "verdict": "OVER" }}
 */
public record LoadBoardRow(Long routeId, String name, String vehicle, VehicleType vehicleType, Integer seats,
		int children, BigDecimal load, int overBy, int spare, BigDecimal yearlyCost, BigDecimal costPerChild,
		BigDecimal feeGot, BigDecimal surplus, LoadBoardCalculator.Verdict verdict) {

	static LoadBoardRow of(Route route, VehicleSummary vehicle, LoadBoardCalculator.RouteLoad load) {
		return new LoadBoardRow(route.getId(), route.getName(), (vehicle != null) ? vehicle.name() : null,
				(vehicle != null) ? vehicle.vehicleType() : null, load.seats(), load.children(), load.load(),
				load.overBy(), load.spare(), load.yearlyCost(), load.costPerChild(), load.feeGot(), load.surplus(),
				load.verdict());
	}

}
