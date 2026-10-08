package com.muhjain.school.analytics;

import java.util.List;

/** All 15 classes in school order (Nursery, LKG, UKG, 1 to 12), also those with 0 children. */
public record StudentsByClassResponse(Long sessionId, String sessionName, int total, List<ClassCount> classes) {

}
