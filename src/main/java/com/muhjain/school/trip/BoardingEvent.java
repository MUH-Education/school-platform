package com.muhjain.school.trip;

import java.time.Instant;
import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One tap. There is at most one row for (student, day, event type): a tap sent twice is one row.
 * Example: Aryan, Route 4, 7 Oct 2026, BOARDED_MORNING, DONE, phone time 07:42:10, recorded by Balwan.
 * Other features are ids, not objects.
 */
@Entity
@Table(name = "boarding_event")
public class BoardingEvent extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	// The route of the child on that day.
	@Column(name = "route_id", nullable = false)
	private Long routeId;

	@Column(name = "service_date", nullable = false)
	private LocalDate serviceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 20)
	private EventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Outcome outcome;

	// The time of the tap on the phone.
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "recorded_by", nullable = false)
	private Long recordedBy;

	// The time the server got it.
	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	protected BoardingEvent() {
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getRouteId() {
		return routeId;
	}

	public LocalDate getServiceDate() {
		return serviceDate;
	}

	public EventType getEventType() {
		return eventType;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Long getRecordedBy() {
		return recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	/** A newer tap replaces the answer of this row. The route may differ if the child moved bus. */
	public void change(Long routeId, Outcome outcome, Instant occurredAt, Long recordedBy, Instant recordedAt) {
		this.routeId = routeId;
		this.outcome = outcome;
		this.occurredAt = occurredAt;
		this.recordedBy = recordedBy;
		this.recordedAt = recordedAt;
	}

}
