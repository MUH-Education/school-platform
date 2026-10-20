package com.muhjain.school.model.dto.request;

import java.time.LocalTime;

/**
 * One stop in {@code PUT /routes/{id}/stops}. The list order is the morning order.
 * A stop with an {@code id} is kept. A stop without an {@code id} is new. {@code nameHindi} is for
 * the attendant app and is optional.
 * Example: {@code { "id": 12, "name": "Jakhal", "nameHindi": "जाखल", "morningTime": "07:40",
 * "eveningTime": "13:50" }}
 */
public record StopRequest(Long id, String name, String nameHindi, LocalTime morningTime, LocalTime eveningTime) {

}
