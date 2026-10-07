package com.muhjain.school.route;

import java.time.LocalTime;

/**
 * One stop in {@code PUT /routes/{id}/stops}. The list order is the morning order.
 * A stop with an {@code id} is kept. A stop without an {@code id} is new.
 * Example: {@code { "id": 12, "name": "Jakhal", "morningTime": "07:40", "eveningTime": "13:50" }}
 */
public record StopRequest(Long id, String name, LocalTime morningTime, LocalTime eveningTime) {

}
