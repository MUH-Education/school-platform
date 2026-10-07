package com.muhjain.school.fee;

import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One school year. Example: "2026-27", 1 Apr 2026 to 31 Mar 2027. Exactly one is current. */
@Entity
@Table(name = "academic_session")
public class AcademicSession extends BaseEntity {

	@Column(nullable = false, length = 9)
	private String name;

	@Column(name = "starts_on", nullable = false)
	private LocalDate startsOn;

	@Column(name = "ends_on", nullable = false)
	private LocalDate endsOn;

	@Column(name = "is_current", nullable = false)
	private boolean current;

	protected AcademicSession() {
	}

	public AcademicSession(String name, LocalDate startsOn, LocalDate endsOn, boolean current) {
		this.name = name;
		this.startsOn = startsOn;
		this.endsOn = endsOn;
		this.current = current;
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

	public boolean isCurrent() {
		return current;
	}

	public void setCurrent(boolean current) {
		this.current = current;
	}

}
