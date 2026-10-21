package com.muhjain.school.util;

import com.muhjain.school.model.dto.response.FamilyDues;
import com.muhjain.school.model.enums.LateFeeKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.TreeSet;

/**
 * Adds up what a family owes from the fee summaries of its children (pure maths, no database).
 * A month counts when it is due and has a balance; it is late when today is past its grace day.
 * Late fee per late month: PER_MONTH = the fixed amount; PER_DAY = amount × days since the grace day, at most the
 * cap; NONE = nothing. Example: PER_DAY 10, cap 300, grace day 10 Sep, today 7 Oct → 27 days → 270.
 */
public final class FamilyDuesCalculator {

	private FamilyDuesCalculator() {
	}

	/** The late fee rule of a session. Null amounts mean 0; a null cap means no cap. */
	public record LateFeeRule(LateFeeKind kind, BigDecimal perMonth, BigDecimal perDay, BigDecimal maxPerMonth) {

		public static final LateFeeRule NONE = new LateFeeRule(LateFeeKind.NONE, null, null, null);

	}

	public static FamilyDues of(Collection<FeeStatusCalculator.Summary> children, LateFeeRule rule, LocalDate today) {
		BigDecimal amount = BigDecimal.ZERO;
		BigDecimal lateFee = BigDecimal.ZERO;
		TreeSet<LocalDate> due = new TreeSet<>();
		TreeSet<LocalDate> late = new TreeSet<>();
		for (FeeStatusCalculator.Summary child : children) {
			amount = amount.add(child.pendingNow());
			for (FeeStatusCalculator.MonthView month : child.months()) {
				if (!month.due() || month.balance().signum() <= 0) {
					continue;
				}
				due.add(month.month());
				if (month.late()) {
					late.add(month.month());
					lateFee = lateFee.add(lateFeeOf(month, rule, today));
				}
			}
		}
		if (amount.signum() <= 0) {
			return FamilyDues.NONE;
		}
		return new FamilyDues(amount, due.stream().toList(), late.size(), lateFee);
	}

	private static BigDecimal lateFeeOf(FeeStatusCalculator.MonthView month, LateFeeRule rule, LocalDate today) {
		return switch (rule.kind()) {
			case NONE -> BigDecimal.ZERO;
			case PER_MONTH -> orZero(rule.perMonth());
			case PER_DAY -> {
				long days = Math.max(0, ChronoUnit.DAYS.between(month.graceDate(), today));
				BigDecimal fee = orZero(rule.perDay()).multiply(BigDecimal.valueOf(days));
				yield (rule.maxPerMonth() == null) ? fee : fee.min(rule.maxPerMonth());
			}
		};
	}

	private static BigDecimal orZero(BigDecimal value) {
		return (value == null) ? BigDecimal.ZERO : value;
	}

}
