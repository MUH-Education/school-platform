package com.muhjain.school.route;

import java.time.LocalTime;

/**
 * A stop with its times, for other features (trips and bus status).
 * Example: {@code StopTimes(12, "Jakhal", 2, 07:40, 14:30)}. Evening order is the morning order reversed.
 */
public record StopTimes(Long id, String name, int seqNo, LocalTime morningTime, LocalTime eveningTime) {

}
