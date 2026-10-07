package com.muhjain.school.student;

/**
 * A warning that does not stop the save. Example: {@code { "code": "ROUTE_FULL",
 * "message": "Route 9 has 45 children on 26 seats." }}
 */
public record TransportWarning(String code, String message) {

}
