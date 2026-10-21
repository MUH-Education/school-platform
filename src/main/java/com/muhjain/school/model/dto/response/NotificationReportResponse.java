package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.ButtonAction;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.MessageProblem;
import com.muhjain.school.model.enums.MessageStatus;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.NotificationStatus;
import com.muhjain.school.model.enums.SkipReason;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The report of one notification: only {@code group by} counts of its outbox rows, never stored (rule 14).
 * The test copy to the sender is not in the counts; it is {@code testCopies}. So {@code byStatus} added up plus
 * {@code testCopies} = {@code select count(*) from message_outbox where notification_id = ?}.
 * Example (fee reminder): 41 rows · gotIt 33 · skipped 8 {ALREADY_PAID: 5, PROMISED: 2, REMINDED_RECENTLY: 1}.
 *
 * @param recipientCount the people who passed the checks at send time
 * @param rows every row (WhatsApp and SMS; URGENT has two per person)
 * @param gotIt rows that went: SENT, DELIVERED, or TEST_ONLY (test mode)
 * @param delivered rows the provider says arrived; {@code read} rows read (WhatsApp)
 * @param byProblem the FAILED rows by reason (no reason given = OTHER)
 * @param byButton the buttons people tapped
 * @param smsCopies SMS sent because the phone had no WhatsApp
 */
public record NotificationReportResponse(Long notificationId, NotificationKind kind, String name,
		NotificationStatus status, Integer recipientCount, OffsetDateTime expandedAt, OffsetDateTime finishedAt,
		long rows, long gotIt, long delivered, long read, long skipped, long failed, long queued, long cancelled,
		Map<MessageStatus, Long> byStatus, Map<MessageChannel, Long> byChannel, Map<SkipReason, Long> bySkipReason,
		Map<MessageProblem, Long> byProblem, Map<ButtonAction, Long> byButton, List<ReadsInHour> readByHour,
		long smsCopies, long testCopies) {
}
