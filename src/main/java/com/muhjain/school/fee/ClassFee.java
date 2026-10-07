package com.muhjain.school.fee;

import java.math.BigDecimal;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The standard school fee of a class in a session. Example: session 2026-27, class "3", 30000.00. */
@Entity
@Table(name = "class_fee")
public class ClassFee extends BaseEntity {

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(name = "class_name", nullable = false, length = 10)
	private String className;

	@Column(name = "school_fee", nullable = false)
	private BigDecimal schoolFee;

	protected ClassFee() {
	}

	public ClassFee(Long sessionId, String className, BigDecimal schoolFee) {
		this.sessionId = sessionId;
		this.className = className;
		this.schoolFee = schoolFee;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public String getClassName() {
		return className;
	}

	public BigDecimal getSchoolFee() {
		return schoolFee;
	}

	public void setSchoolFee(BigDecimal schoolFee) {
		this.schoolFee = schoolFee;
	}

}
