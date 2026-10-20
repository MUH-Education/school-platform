package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.entity.Guardian;
import com.muhjain.school.model.entity.StudentGuardian;
import com.muhjain.school.model.enums.GuardianRelation;

/**
 * One phone of a child. Example: {@code { "guardianId": 31, "name": "Ramesh", "phone": "+919812340208",
 * "relation": "GRANDFATHER", "smsEnabled": false, "primary": false }}
 * {@code smsEnabled} and {@code primary} belong to this child only. The phone and the name are shared by every
 * child who uses the number.
 */
public record GuardianResponse(Long guardianId, String name, String phone, GuardianRelation relation,
		boolean smsEnabled, boolean primary) {

	public static GuardianResponse of(Guardian guardian, StudentGuardian link) {
	return new GuardianResponse(guardian.getId(), guardian.getName(), guardian.getPhone(), link.getRelation(),
				link.isSmsEnabled(), link.isPrimary());
	}

}
