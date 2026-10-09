package com.muhjain.school.staff;

/**
 * Which paper the office saw. Only the last 4 digits of the number are stored, never the whole number
 * (question C11 in docs/08-decisions.md).
 * Example: AADHAAR with {@code idProofLast4} "4321" means the office saw an Aadhaar card ending 4321.
 */
public enum IdProofType {

	AADHAAR, VOTER_ID, PAN, DRIVING_LICENCE

}
