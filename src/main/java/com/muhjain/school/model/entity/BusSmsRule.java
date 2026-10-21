package com.muhjain.school.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Which of the four bus events send an SMS, for a group of classes. Every class is in exactly one group
 * (checked in the service). Example: "Nursery to Class 8", all four events on.
 */
@Entity
@Table(name = "bus_sms_rule")
public class BusSmsRule extends BaseEntity {

	@Column(name = "seq_no", nullable = false)
	private int seqNo;

	@Column(nullable = false, length = 60)
	private String name;

	@Column(name = "from_class", nullable = false, length = 10)
	private String fromClass;

	@Column(name = "to_class", nullable = false, length = 10)
	private String toClass;

	@Column(name = "boarded_morning", nullable = false)
	private boolean boardedMorning;

	@Column(name = "reached_school", nullable = false)
	private boolean reachedSchool;

	@Column(name = "boarded_evening", nullable = false)
	private boolean boardedEvening;

	@Column(name = "reached_home", nullable = false)
	private boolean reachedHome;

	@Column(name = "updated_by")
	private Long updatedBy;

	protected BusSmsRule() {
	}

	public int getSeqNo() {
		return seqNo;
	}

	public String getName() {
		return name;
	}

	public String getFromClass() {
		return fromClass;
	}

	public String getToClass() {
		return toClass;
	}

	public boolean isBoardedMorning() {
		return boardedMorning;
	}

	public boolean isReachedSchool() {
		return reachedSchool;
	}

	public boolean isBoardedEvening() {
		return boardedEvening;
	}

	public boolean isReachedHome() {
		return reachedHome;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

}
