package com.muhjain.school.model.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.muhjain.school.model.enums.OwnedBy;
import com.muhjain.school.model.enums.VehicleType;

/**
 * Add a vehicle.
 * Example: {@code { "name": "Van 4", "registrationNo": "HR 23 A 1104", "vehicleType": "SMALL_VAN", "seats": 14,
 * "monthlyCost": 30300, "ownedBy": "CONTRACTOR" }}
 */
public record CreateVehicleRequest(@NotBlank @Size(max = 40) String name,
		@NotBlank @Size(max = 20) String registrationNo, @NotNull VehicleType vehicleType,
		@NotNull @Positive Integer seats, @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal monthlyCost,
		@NotNull OwnedBy ownedBy) {

}
