package com.muhjain.school.model.dto.response;

import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.muhjain.school.model.dto.response.VehicleSummary;
import com.muhjain.school.model.enums.VehicleType;
import com.muhjain.school.model.entity.Route;

/**
 * One route with its vehicle and stops in order. {@code schoolDueMorning}/{@code schoolDueEvening} are the time
 * the route is due at school, null until the office sets them.
 * Example: {@code { "id": 4, "name": "Route 4", "active": true,
 * "vehicle": { "id": 4, "name": "Van 4", "vehicleType": "SMALL_VAN", "seats": 14 },
 * "schoolDueMorning": "08:10", "schoolDueEvening": "14:35", "stops": [ ... ] }}
 */
public record RouteResponse(Long id, String name, boolean active, VehicleRef vehicle,
		@JsonFormat(pattern = "HH:mm") LocalTime schoolDueMorning,
		@JsonFormat(pattern = "HH:mm") LocalTime schoolDueEvening, List<StopResponse> stops) {

	/** The vehicle that runs the route, in short. Null when the route has no vehicle yet. */
	public record VehicleRef(Long id, String name, VehicleType vehicleType, int seats) {

		public static VehicleRef of(VehicleSummary vehicle) {
			return (vehicle == null) ? null
					: new VehicleRef(vehicle.id(), vehicle.name(), vehicle.vehicleType(), vehicle.seats());
		}

	}

}
