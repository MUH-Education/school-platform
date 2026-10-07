package com.muhjain.school.staff;

/**
 * Where a person works on one day.
 * Example: {@code { "vehicleId": 4, "vehicleName": "Van 4", "duty": "DRIVER", "temporary": true }}
 * means Surender is the temporary driver of Van 4 that day.
 */
public record Placement(Long vehicleId, String vehicleName, Duty duty, boolean temporary) {

}
