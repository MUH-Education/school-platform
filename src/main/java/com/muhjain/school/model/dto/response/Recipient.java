package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.RecipientType;

import java.util.List;
import java.util.Map;

/**
 * One person a notification goes to, the same shape for every audience group, so the notification code never cares
 * which group it is reading. A family is one recipient however many children it has.
 * Example: GUARDIAN 210, +919811100001, WHATSAPP, CURRENT_PARENTS, children [118, 119],
 * {"Name": "Rajesh Punia", "Parent name": "Rajesh Punia", "Child name": "Aryan और Siya", "Class": "3 और 11"}.
 *
 * @param personId guardian id, staff id or enquiry id (see {@code type})
 * @param phone {@code +91XXXXXXXXXX}, or null when the person has no phone (counted as "no phone", never sent)
 * @param group the audience group that found this person first
 * @param studentIds the ACTIVE children of a family; empty for staff and enquiries
 * @param values the {box} values of this person
 */
public record Recipient(RecipientType type, Long personId, String phone, MessageChannel channel, AudienceGroup group,
		List<Long> studentIds, Map<String, String> values) {

	public Recipient {
		studentIds = (studentIds == null) ? List.of() : List.copyOf(studentIds);
		values = (values == null) ? Map.of() : Map.copyOf(values);
	}

}
