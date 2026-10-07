package com.muhjain.school.trip;

import java.util.List;

/** One result per tap, in the same order as the request. */
public record MarksResponse(List<MarkResult> results) {

}
