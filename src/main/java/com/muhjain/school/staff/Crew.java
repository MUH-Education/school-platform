package com.muhjain.school.staff;

/**
 * The people on a vehicle on one day. A duty nobody does is null.
 * Example: Van 4 on 14 Oct 2026 → driver Surender (temporary), attendant Balwan, no helper.
 */
public record Crew(CrewMember driver, CrewMember attendant, CrewMember helper) {

	public static final Crew EMPTY = new Crew(null, null, null);

}
