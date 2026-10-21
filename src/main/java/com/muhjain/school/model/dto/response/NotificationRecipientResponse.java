package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.MessageStatus;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.model.enums.SkipReason;

/**
 * One line of "See the 246 families". Before sending it is worked out now (status null); after, it is the outbox
 * row (status, skip reason). The phone is masked like everywhere: +91XXXXXX0001.
 * Example: GUARDIAN 210, "Rajesh Punia", "Aryan और Siya", +91XXXXXX0001, WHATSAPP, CURRENT_PARENTS.
 */
public record NotificationRecipientResponse(RecipientType type, Long personId, String name, String childName,
		String phone, MessageChannel channel, AudienceGroup group, MessageStatus status, SkipReason skipReason) {
}
