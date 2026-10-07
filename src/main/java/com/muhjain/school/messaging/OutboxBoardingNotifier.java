package com.muhjain.school.messaging;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.student.Gender;
import com.muhjain.school.student.SmsTarget;
import com.muhjain.school.student.StudentQueryService;
import com.muhjain.school.trip.BoardingNotifier;
import com.muhjain.school.trip.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the parent SMS into the queue (rules 2 to 7 of the phase file). It runs inside the transaction that saves
 * the tap ({@code MANDATORY}), so a tap that is rolled back leaves no SMS.
 * <p>
 * Example: Aryan (class 3, boy) is tapped BOARDED_MORNING at 7:42 today → one QUEUED row for each parent phone with
 * SMS on, text "Aryan सुबह की बस में चढ़ गया — 7:42। MUH Jain School". Class 11, or a tap for yesterday → no row.
 * The caller only calls this for outcome DONE.
 */
@Component
public class OutboxBoardingNotifier implements BoardingNotifier {

	private static final Logger log = LoggerFactory.getLogger(OutboxBoardingNotifier.class);

	private final StudentQueryService studentQuery;

	private final MessageTemplateRepository templates;

	private final MessageOutboxRepository outbox;

	private final Clock clock;

	public OutboxBoardingNotifier(StudentQueryService studentQuery, MessageTemplateRepository templates,
			MessageOutboxRepository outbox, Clock clock) {
		this.studentQuery = studentQuery;
		this.templates = templates;
		this.outbox = outbox;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void onDone(Long studentId, EventType eventType, LocalDate serviceDate, Instant occurredAt) {
		// Rule 4: a tap for another day (a phone that was off) is saved, but nobody gets an SMS.
		if (!serviceDate.equals(LocalDate.now(clock))) {
			return;
		}
		studentQuery.smsTarget(studentId).ifPresent(child -> queue(child, eventType, serviceDate, occurredAt));
	}

	private void queue(SmsTarget child, EventType eventType, LocalDate serviceDate, Instant occurredAt) {
		if (!SmsPolicy.allows(child.className(), eventType) || child.phones().isEmpty()) {
			return;
		}
		String code = eventType.name() + ((child.gender() == Gender.F) ? "_F" : "_M");
		MessageTemplate template = templates.findById(code).filter(MessageTemplate::isActive).orElse(null);
		if (template == null) {
			log.warn("No active message template {}, no SMS queued for student {}", code, child.studentId());
			return;
		}
		LocalTime time = occurredAt.atZone(clock.getZone()).toLocalTime();
		String body = SmsTextBuilder.build(template.getBody(), child.name(), time);
		Instant now = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
		for (SmsTarget.Phone phone : child.phones()) {
			int saved = outbox.queueBoarding(phone.phone(), phone.guardianId(), child.studentId(), serviceDate,
					eventType.name(), code, body, now);
			if (saved == 0) {
				log.debug("SMS for {} on {} {} was queued before", PhoneNumbers.mask(phone.phone()), serviceDate,
						eventType);
			}
		}
	}

}
