package com.muhjain.school.staff;

/**
 * The job a person does on a vehicle. A DRIVER duty needs a staff member of type DRIVER, and so on (rule 11).
 */
public enum Duty {

	DRIVER(StaffType.DRIVER), ATTENDANT(StaffType.ATTENDANT), HELPER(StaffType.HELPER);

	private final StaffType requiredType;

	Duty(StaffType requiredType) {
		this.requiredType = requiredType;
	}

	/** The staff type that may do this duty. */
	public StaffType requiredType() {
		return requiredType;
	}

}
