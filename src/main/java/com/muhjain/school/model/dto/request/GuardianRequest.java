package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.GuardianRelation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A phone number of a parent or relative. {@code smsEnabled} is optional and means "yes" when it is missing.
 * Example: {@code { "name": "Ramesh", "phone": "98123 40208", "relation": "GRANDFATHER", "smsEnabled": false }}
 */
public record GuardianRequest(@Size(max = 120) String name, @NotBlank String phone, @NotNull GuardianRelation relation,
		Boolean smsEnabled) {

	public boolean sms() {
		return smsEnabled == null || smsEnabled;
	}

}
