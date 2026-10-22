package com.muhjain.school.model.dto.response;

/**
 * A child on a bus today who has no morning tap yet, on a route whose attendant has tapped someone else.
 * Example: Aryan, class 3, Route 4 · Jakhal.
 */
public record UntappedChild(Long studentId, String name, String className, Long routeId, String routeName) {

}
