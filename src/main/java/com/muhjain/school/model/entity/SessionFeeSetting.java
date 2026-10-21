package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.LateFeeKind;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * The fee rules of one session: bus fee, which months have a bus fee, due day, grace, late fee. One row per
 * session. Example: 800 a month bus fee, no bus fee in June, due day 1, grace until 10, late fee 100 a month.
 */
@Entity
@Table(name = "session_fee_setting")
public class SessionFeeSetting extends BaseEntity {

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(name = "bus_fee_per_month", nullable = false, precision = 12, scale = 2)
	private BigDecimal busFeePerMonth;

	// Month numbers (1-12) that have a bus fee. Example: {4,5,7,8,9,10,11,12,1,2,3} = no fee in June.
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "bus_months", nullable = false)
	private Integer[] busMonths;

	@Column(name = "due_day", nullable = false)
	private int dueDay = 1;

	// 31 means "end of the month".
	@Column(name = "grace_until", nullable = false)
	private int graceUntil = 10;

	@Enumerated(EnumType.STRING)
	@Column(name = "late_fee_kind", nullable = false, length = 10)
	private LateFeeKind lateFeeKind = LateFeeKind.PER_MONTH;

	@Column(name = "late_fee_per_month", precision = 12, scale = 2)
	private BigDecimal lateFeePerMonth;

	@Column(name = "late_fee_per_day", precision = 12, scale = 2)
	private BigDecimal lateFeePerDay;

	@Column(name = "late_fee_max_per_month", precision = 12, scale = 2)
	private BigDecimal lateFeeMaxPerMonth;

	// Role names (OWNER, OFFICE_ADMIN, ...) allowed to change the late fee of a receipt away from the standard.
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "late_fee_change_roles", nullable = false)
	private String[] lateFeeChangeRoles;

	@Column(name = "fees_set_by_owner", nullable = false)
	private boolean feesSetByOwner;

	@Column(name = "updated_by")
	private Long updatedBy;

	protected SessionFeeSetting() {
	}

	public SessionFeeSetting(Long sessionId, BigDecimal busFeePerMonth, Integer[] busMonths, int dueDay,
			int graceUntil, LateFeeKind lateFeeKind, BigDecimal lateFeePerMonth, BigDecimal lateFeePerDay,
			BigDecimal lateFeeMaxPerMonth, String[] lateFeeChangeRoles, boolean feesSetByOwner) {
		this.sessionId = sessionId;
		this.busFeePerMonth = busFeePerMonth;
		this.busMonths = busMonths;
		this.dueDay = dueDay;
		this.graceUntil = graceUntil;
		this.lateFeeKind = lateFeeKind;
		this.lateFeePerMonth = lateFeePerMonth;
		this.lateFeePerDay = lateFeePerDay;
		this.lateFeeMaxPerMonth = lateFeeMaxPerMonth;
		this.lateFeeChangeRoles = lateFeeChangeRoles;
		this.feesSetByOwner = feesSetByOwner;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public BigDecimal getBusFeePerMonth() {
		return busFeePerMonth;
	}

	public void setBusFeePerMonth(BigDecimal busFeePerMonth) {
		this.busFeePerMonth = busFeePerMonth;
	}

	public Integer[] getBusMonths() {
		return busMonths;
	}

	public void setBusMonths(Integer[] busMonths) {
		this.busMonths = busMonths;
	}

	public int getDueDay() {
		return dueDay;
	}

	public void setDueDay(int dueDay) {
		this.dueDay = dueDay;
	}

	public int getGraceUntil() {
		return graceUntil;
	}

	public void setGraceUntil(int graceUntil) {
		this.graceUntil = graceUntil;
	}

	public LateFeeKind getLateFeeKind() {
		return lateFeeKind;
	}

	public void setLateFeeKind(LateFeeKind lateFeeKind) {
		this.lateFeeKind = lateFeeKind;
	}

	public BigDecimal getLateFeePerMonth() {
		return lateFeePerMonth;
	}

	public void setLateFeePerMonth(BigDecimal lateFeePerMonth) {
		this.lateFeePerMonth = lateFeePerMonth;
	}

	public BigDecimal getLateFeePerDay() {
		return lateFeePerDay;
	}

	public void setLateFeePerDay(BigDecimal lateFeePerDay) {
		this.lateFeePerDay = lateFeePerDay;
	}

	public BigDecimal getLateFeeMaxPerMonth() {
		return lateFeeMaxPerMonth;
	}

	public void setLateFeeMaxPerMonth(BigDecimal lateFeeMaxPerMonth) {
		this.lateFeeMaxPerMonth = lateFeeMaxPerMonth;
	}

	public String[] getLateFeeChangeRoles() {
		return lateFeeChangeRoles;
	}

	public void setLateFeeChangeRoles(String[] lateFeeChangeRoles) {
		this.lateFeeChangeRoles = lateFeeChangeRoles;
	}

	public boolean isFeesSetByOwner() {
		return feesSetByOwner;
	}

	public void setFeesSetByOwner(boolean feesSetByOwner) {
		this.feesSetByOwner = feesSetByOwner;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}

	/** Does month m (1-12) of this session have a bus fee? */
	public boolean hasBusFeeInMonth(int month) {
		for (int m : busMonths) {
			if (m == month) {
				return true;
			}
		}
		return false;
	}

}
