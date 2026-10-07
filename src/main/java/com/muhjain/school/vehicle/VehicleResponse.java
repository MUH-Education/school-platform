package com.muhjain.school.vehicle;

import java.math.BigDecimal;
import java.util.List;

/**
 * One vehicle. {@code documents} always has the four papers, in the order FITNESS, INSURANCE, PERMIT, PUC.
 * {@code papersStatus} is the worst of the four.
 * Example: {@code { "id": 4, "name": "Van 4", "registrationNo": "HR 23 A 1104", "vehicleType": "SMALL_VAN",
 * "seats": 14, "monthlyCost": 30300.00, "ownedBy": "CONTRACTOR", "active": true, "papersStatus": "ENDING_SOON",
 * "documents": [ ... ] }}
 */
public record VehicleResponse(Long id, String name, String registrationNo, VehicleType vehicleType, int seats,
		BigDecimal monthlyCost, OwnedBy ownedBy, boolean active, PaperStatus papersStatus,
		List<DocumentResponse> documents) {

	static VehicleResponse of(Vehicle vehicle, List<DocumentResponse> documents) {
		return new VehicleResponse(vehicle.getId(), vehicle.getName(), vehicle.getRegistrationNo(),
				vehicle.getVehicleType(), vehicle.getSeats(), vehicle.getMonthlyCost(), vehicle.getOwnedBy(),
				vehicle.isActive(),
				PaperStatus.worst(documents.stream().map(DocumentResponse::status).toList()), documents);
	}

}
