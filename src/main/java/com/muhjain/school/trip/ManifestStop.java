package com.muhjain.school.trip;

import java.time.LocalTime;
import java.util.List;

/** A stop with the children who board there. Example: Jakhal, due 07:40 and 14:30, 6 children. */
public record ManifestStop(Long stopId, String name, int seqNo, LocalTime morningTime, LocalTime eveningTime,
		List<ManifestChild> children) {

}
