package com.muhjain.school.route;

import java.util.List;

import com.muhjain.school.vehicle.VehicleSummary;
import com.muhjain.school.vehicle.VehicleType;

/**
 * One route with its vehicle and stops in order.
 * Example: {@code { "id": 4, "name": "Route 4", "active": true,
 * "vehicle": { "id": 4, "name": "Van 4", "vehicleType": "SMALL_VAN", "seats": 14 }, "stops": [ ... ] }}
 */
public record RouteResponse(Long id, String name, boolean active, VehicleRef vehicle, List<StopResponse> stops) {

	/** The vehicle that runs the route, in short. Null when the route has no vehicle yet. */
	public record VehicleRef(Long id, String name, VehicleType vehicleType, int seats) {

		static VehicleRef of(VehicleSummary vehicle) {
			return (vehicle == null) ? null
					: new VehicleRef(vehicle.id(), vehicle.name(), vehicle.vehicleType(), vehicle.seats());
		}

	}

}
