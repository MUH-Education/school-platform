package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.SchoolEvent;
import com.muhjain.school.model.enums.RuleEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The one line other services call when something happens that a rule may answer (Phase 12, rule 4). It only
 * publishes a Spring event; {@link RuleEventListener} handles it **after** the caller's transaction has committed,
 * so a rolled-back admission never sends a welcome. Must be called inside a transaction (else it is never handled).
 * Example: {@code ruleEvents.admissionSaved(129, 210)} at the end of {@code AdmissionService.admit}.
 */
@Component
public class RuleEvents {

	private final ApplicationEventPublisher publisher;

	public RuleEvents(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/** A new child is admitted → WELCOME, to that family. */
	public void admissionSaved(Long studentId, Long guardianId) {
		publisher.publishEvent(new SchoolEvent(RuleEvent.ADMISSION_SAVED, studentId, guardianId, null, null));
	}

	/** The attendant tapped "running late" → BUS_LATE, to the families on that route today. */
	public void busLate(Long routeId, int minutes) {
		publisher.publishEvent(new SchoolEvent(RuleEvent.BUS_LATE, null, null, routeId, minutes));
	}

	/** Money was received → the family's waiting fee reminders are skipped at once. */
	public void paymentSaved(Long studentId, Long guardianId) {
		publisher.publishEvent(new SchoolEvent(RuleEvent.PAYMENT_SAVED, studentId, guardianId, null, null));
	}

}
