package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.MediaFileType;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.NotificationSource;
import com.muhjain.school.model.enums.NotificationStatus;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One notification. {@code counts} are worked out now from the live data, for a DRAFT or SCHEDULED one opened
 * alone ("Who gets it"); null in lists and once it is sent ({@code recipientCount} is the number at send time).
 * Example: 17, NOTICE "PTM Class 10", SCHEDULED for 10 Oct 10:00, counts total 17.
 * A notification made by an automatic rule has {@code source RULE} and the rule's id and name, so Home can show
 * "Automatic · Fee reminder 10th" as the sender ({@code createdByName} is the owner who turned the rule on).
 */
public record NotificationResponse(Long id, NotificationKind kind, String name, Long templateId, String templateName,
		String body, String smsBody, Map<String, String> fixedValues, String mediaKey, MediaFileType mediaType,
		Audience audience, NotificationStatus status, OffsetDateTime scheduledAt, OffsetDateTime expandedAt,
		OffsetDateTime finishedAt, Integer recipientCount, String note, NotificationSource source, Long ruleId,
		String ruleName, Long createdBy, String createdByName, OffsetDateTime createdAt, AudienceCounts counts) {
}
