package com.muhjain.school.student;

/** Who the phone belongs to, from the child's side. Example: Siya's grandfather → GRANDFATHER. */
public enum GuardianRelation {

	FATHER("Father"), MOTHER("Mother"), GRANDFATHER("Grandfather"), GRANDMOTHER("Grandmother"),
	UNCLE_AUNT("Uncle or aunt"), OTHER("Other");

	private final String label;

	GuardianRelation(String label) {
		this.label = label;
	}

	/** For the change history. Example: GRANDFATHER → "Grandfather". */
	public String label() {
		return label;
	}

}
