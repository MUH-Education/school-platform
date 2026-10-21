package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.OwnedBy;
import com.muhjain.school.model.enums.VehicleType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Change a vehicle. Send the whole vehicle, like the edit form shows it. Same as the Phase 1 user form.
 * {@code active: false} turns the vehicle off (same rule as DELETE). {@code active: true} turns it on again.
 */
public record UpdateVehicleRequest(@NotBlank @Size(max = 40) String name,
		@NotBlank @Size(max = 20) String registrationNo, @NotNull VehicleType vehicleType,
		@NotNull @Positive Integer seats, @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal monthlyCost,
		@NotNull OwnedBy ownedBy, @NotNull Boolean active) {

}
