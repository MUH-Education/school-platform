package com.muhjain.school.service.notification;

import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessagePurpose;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;

import java.util.Set;

/**
 * The rules of one kind of notification (docs/10-notifications.md section 2). One small class per kind, in
 * {@code impl}; {@link KindPolicies} finds them by kind. To add a kind: one enum value, one class, one Flyway
 * {@code check} change. Nothing else.
 * Example: MARKETING goes to parents, enquiries and staff, skips the stop list, respects quiet hours.
 */
public interface KindPolicy {

	NotificationKind kind();

	/** The audience groups this kind may go to. Any other group → 409 AUDIENCE_NOT_ALLOWED. */
	Set<AudienceGroup> allowedAudiences();

	/** True = families on the stop list are skipped (MARKETING, GREETING). */
	boolean stopListApplies();

	/** True = sent at once, also at night, and never refused for quiet hours (URGENT). */
	boolean ignoresQuietHours();

	/** True = a person on WhatsApp also gets the SMS (URGENT: everyone gets both). */
	boolean alsoSmsWhenOnWhatsApp();

	/**
	 * True = free text without an approved template is allowed, but only to a person who wrote to the school in
	 * the last 24 hours (WhatsApp's rule). ONE_FAMILY and STAFF.
	 */
	boolean freeTextInsideWindow();

	/**
	 * The user needs at least one of these. For now every kind returns {@code MARKETING_SEND} (owner only,
	 * docs/08-decisions.md); opening a kind to the office later is a change here only.
	 */
	Set<Permission> whoMaySend();

	/** The {@code purpose} of the outbox rows. Example: GREETING → MARKETING (WhatsApp counts it as marketing). */
	MessagePurpose outboxPurpose();

}
