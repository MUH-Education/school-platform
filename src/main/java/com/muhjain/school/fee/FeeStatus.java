package com.muhjain.school.fee;

/**
 * How a family is doing with its fees. Never stored, always calculated. The order is the order of "worse":
 * ON_TIME is the best, DEFAULTED the worst.
 */
public enum FeeStatus {

	ON_TIME, DELAYED, DEFAULTED

}
