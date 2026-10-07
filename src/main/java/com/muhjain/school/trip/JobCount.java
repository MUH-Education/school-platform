package com.muhjain.school.trip;

/**
 * Progress of one of the four jobs. {@code remaining} = children with no tap yet.
 * Example: 19 children, 11 done, 1 absent → {@code JobCount(11, 1, 0, 7)}.
 */
public record JobCount(int done, int absent, int notTravelling, int remaining) {

}
