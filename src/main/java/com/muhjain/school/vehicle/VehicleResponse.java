package com.muhjain.school.vehicle;

import java.math.BigDecimal;
import java.util.List;

import com.muhjain.school.staff.Crew;
import com.muhjain.school.staff.CrewMember;

/**
 * One vehicle. {@code documents} always has the four papers, in the order FITNESS, INSURANCE, PERMIT, PUC.
 * {@code papersStatus} is the worst of the four.
 * {@code driver}, {@code attendant}, {@code helper} are the people on the vehicle on the asked day (today if no day
 * is asked), or null if nobody does that duty. A person with {@code temporary: true} is a replacement.
 * Example: {@code { "id": 4, "name": "Van 4", "registrationNo": "HR 23 A 1104", "vehicleType": "SMALL_VAN",
 * "seats": 14, "monthlyCost": 30300.00, "ownedBy": "CONTRACTOR", "active": true, "papersStatus": "ENDING_SOON",
 * "documents": [ ... ] }}
 */
public record VehicleResponse(Long id, String name, String registrationNo, VehicleType vehicleType, int seats,
		BigDecimal monthlyCost, OwnedBy ownedBy, boolean active, PaperStatus papersStatus,
		List<DocumentResponse> documents, CrewMember driver, CrewMember attendant, CrewMember helper) {

	// The people are filled in by VehicleOverviewService. VehicleService does not know about staff.
	static VehicleResponse of(Vehicle vehicle, List<DocumentResponse> documents) {
		return new VehicleResponse(vehicle.getId(), vehicle.getName(), vehicle.getRegistrationNo(),
				vehicle.getVehicleType(), vehicle.getSeats(), vehicle.getMonthlyCost(), vehicle.getOwnedBy(),
				vehicle.isActive(),
				PaperStatus.worst(documents.stream().map(DocumentResponse::status).toList()), documents, null, null,
				null);
	}

	/** The same vehicle with the people of one day. */
	VehicleResponse withCrew(Crew crew) {
		return new VehicleResponse(id, name, registrationNo, vehicleType, seats, monthlyCost, ownedBy, active,
				papersStatus, documents, crew.driver(), crew.attendant(), crew.helper());
	}

}
