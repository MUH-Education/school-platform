package com.muhjain.school.staff;

/**
 * A person on a vehicle on one day.
 * Example: {@code { "staffId": 21, "name": "Surender", "phone": "+919812340021", "temporary": true }}.
 * {@code temporary} is true when the person is a replacement for some days.
 */
public record CrewMember(Long staffId, String name, String phone, boolean temporary) {

}
