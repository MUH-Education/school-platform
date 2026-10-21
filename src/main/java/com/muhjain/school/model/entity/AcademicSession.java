package com.muhjain.school.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One school year. Example: "2026-27", 1 Apr 2026 to 31 Mar 2027.
 * "Current" is not stored: it is the session whose dates include today (see {@link #covers}).
 */
@Entity
@Table(name = "academic_session")
public class AcademicSession extends BaseEntity {

	@Column(nullable = false, length = 7)
	private String name;

	@Column(name = "starts_on", nullable = false)
	private LocalDate startsOn;

	@Column(name = "ends_on", nullable = false)
	private LocalDate endsOn;

	protected AcademicSession() {
	}

	public AcademicSession(String name, LocalDate startsOn, LocalDate endsOn) {
		this.name = name;
		this.startsOn = startsOn;
		this.endsOn = endsOn;
	}

	/** Example: a session of 2026-04-01 to 2027-03-31 covers 2026-10-07. */
	public boolean covers(LocalDate date) {
		return !date.isBefore(startsOn) && !date.isAfter(endsOn);
	}

	public String getName() {
		return name;
	}

	public LocalDate getStartsOn() {
		return startsOn;
	}

	public LocalDate getEndsOn() {
		return endsOn;
	}

}
