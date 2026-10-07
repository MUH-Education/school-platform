package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** One amount that must be paid by one date. Example: SCHOOL, 1 Jul 2026, 7500.00. */
@Entity
@Table(name = "fee_due")
public class FeeDue extends BaseEntity {

	@Column(name = "fee_plan_id", nullable = false)
	private Long feePlanId;

	@Enumerated(EnumType.STRING)
	@Column(name = "fee_head", nullable = false, length = 10)
	private FeeHead feeHead;

	@Column(name = "due_on", nullable = false)
	private LocalDate dueOn;

	@Column(nullable = false)
	private BigDecimal amount;

	protected FeeDue() {
	}

	public FeeDue(Long feePlanId, FeeHead feeHead, LocalDate dueOn, BigDecimal amount) {
		this.feePlanId = feePlanId;
		this.feeHead = feeHead;
		this.dueOn = dueOn;
		this.amount = amount;
	}

	public Long getFeePlanId() {
		return feePlanId;
	}

	public FeeHead getFeeHead() {
		return feeHead;
	}

	public LocalDate getDueOn() {
		return dueOn;
	}

	public BigDecimal getAmount() {
		return amount;
	}

}
