package com.muhjain.school.model.entity;

import java.math.BigDecimal;

import com.muhjain.school.model.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The standard school fee of a class in a session, a month. Example: session 2026-27, class "3", 2500.00. */
@Entity
@Table(name = "class_fee")
public class ClassFee extends BaseEntity {

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(name = "class_name", nullable = false, length = 10)
	private String className;

	@Column(name = "per_month", nullable = false)
	private BigDecimal perMonth;

	protected ClassFee() {
	}

	public ClassFee(Long sessionId, String className, BigDecimal perMonth) {
		this.sessionId = sessionId;
		this.className = className;
		this.perMonth = perMonth;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public String getClassName() {
		return className;
	}

	public BigDecimal getPerMonth() {
		return perMonth;
	}

	public void setPerMonth(BigDecimal perMonth) {
		this.perMonth = perMonth;
	}

}
