package com.muhjain.school.student;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Change a phone of one child: name, relation, SMS on or off. The number itself is not changed here: remove the
 * old phone and add the new one. {@code smsEnabled} is for this child only.
 * Example: the grandfather wants no SMS for Siya: {@code { "name": "Ramesh", "relation": "GRANDFATHER",
 * "smsEnabled": false }}
 */
public record UpdateGuardianRequest(@Size(max = 120) String name, @NotNull GuardianRelation relation,
		@NotNull Boolean smsEnabled) {

}
