package com.muhjain.school.trip;

/**
 * Where a route is. Calculated from taps, never stored.
 * Morning: NOT_STARTED, NO_TAPS, ON_THE_WAY, LATE, REACHED_SCHOOL.
 * Evening: NOT_STARTED, BOARDING, ON_THE_WAY, DONE.
 */
public enum RouteState {

	NOT_STARTED, NO_TAPS, ON_THE_WAY, LATE, REACHED_SCHOOL, BOARDING, DONE

}
