package com.muhjain.school.model.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/** A day with no school (Sundays are always off and are not stored). Example: 20 Oct 2026, "Diwali". */
@Entity
@Table(name = "holiday")
public class Holiday extends BaseEntity {

	@Column(name = "holiday_date", nullable = false, unique = true)
	private LocalDate holidayDate;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "created_by", nullable = false, updatable = false)
	private Long createdBy;

	protected Holiday() {
	}

	public LocalDate getHolidayDate() {
		return holidayDate;
	}

	public String getName() {
		return name;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

}
