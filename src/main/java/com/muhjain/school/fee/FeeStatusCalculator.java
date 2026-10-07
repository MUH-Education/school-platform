package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The fee maths of one child (rule 15 of phase 7, "How fee status is calculated" in docs/03-data-model.md).
 * Pure Java: no Spring, no database, no clock. The caller gives "today" and the two settings.
 * <p>
 * For one fee head on day D:
 * <ol>
 * <li>Payments cover the dues oldest first.</li>
 * <li>{@code pendingNow} = dues up to D that are not covered. {@code remaining} = all dues not covered.</li>
 * <li>Nothing pending → ON_TIME. Otherwise take the oldest due that is not fully covered:
 * {@code daysLate <= graceDays} → ON_TIME, {@code <= defaultedAfterDays} → DELAYED, more → DEFAULTED.</li>
 * </ol>
 * The child's status is the worse of SCHOOL and BUS.
 * <p>
 * Example on 7 Oct: dues of ₹7,500 on 1 Apr, 1 Jul and 1 Oct, the family paid ₹15,000. The 1 Oct due is not
 * covered, it is 6 days late, grace is 10 → ON_TIME, pendingNow ₹7,500.
 */
public final class FeeStatusCalculator {

	/** One due, as it comes from the database. */
	public record DueInput(Long id, FeeHead head, LocalDate dueOn, BigDecimal amount) {
	}

	/** One due with the part of it that payments cover. Example: ₹7,500 due, ₹2,000 covered. */
	public record CoveredDue(Long id, FeeHead head, LocalDate dueOn, BigDecimal amount, BigDecimal covered) {
	}

	/** The numbers of one fee head. {@code oldestUnpaidDue} and {@code daysLate} are null / 0 when nothing is pending. */
	public record HeadStatus(FeeHead head, FeeStatus status, BigDecimal paid, BigDecimal pendingNow,
			BigDecimal remaining, LocalDate oldestUnpaidDue, long daysLate) {
	}

	/**
	 * Everything for the screen. {@code nextDueOn} is the first day after today with money not yet covered, and
	 * {@code nextDueAmount} the sum of both heads on that day (null when nothing is coming).
	 */
	public record Summary(List<CoveredDue> dues, Map<FeeHead, HeadStatus> heads, BigDecimal pendingNow,
			BigDecimal remainingThisYear, LocalDate nextDueOn, BigDecimal nextDueAmount, FeeStatus status) {
	}

	private FeeStatusCalculator() {
	}

	/**
	 * @param dues the dues of the plan, both heads, any order
	 * @param paid money received per head, corrections already taken off. A head with no entry means 0.
	 * @param graceDays {@code fees.grace_days}, example 10
	 * @param defaultedAfterDays {@code fees.defaulted_after_days}, example 60
	 */
	public static Summary calculate(List<DueInput> dues, Map<FeeHead, BigDecimal> paid, LocalDate today,
			int graceDays, int defaultedAfterDays) {
		List<CoveredDue> covered = new ArrayList<>();
		Map<FeeHead, HeadStatus> heads = new EnumMap<>(FeeHead.class);
		FeeStatus worst = FeeStatus.ON_TIME;
		BigDecimal pendingNow = BigDecimal.ZERO;
		BigDecimal remaining = BigDecimal.ZERO;
		for (FeeHead head : FeeHead.values()) {
			List<DueInput> ofHead = dues.stream()
				.filter(d -> d.head() == head)
				.sorted(Comparator.comparing(DueInput::dueOn).thenComparing(d -> (d.id() == null) ? 0L : d.id()))
				.toList();
			BigDecimal money = paid.getOrDefault(head, BigDecimal.ZERO).max(BigDecimal.ZERO);
			BigDecimal paidTotal = money;
			BigDecimal headPending = BigDecimal.ZERO;
			BigDecimal headRemaining = BigDecimal.ZERO;
			LocalDate oldestUnpaid = null;
			for (DueInput due : ofHead) {
				BigDecimal part = money.min(due.amount());
				money = money.subtract(part);
				covered.add(new CoveredDue(due.id(), head, due.dueOn(), due.amount(), part));
				BigDecimal open = due.amount().subtract(part);
				if (open.signum() > 0) {
					headRemaining = headRemaining.add(open);
					if (oldestUnpaid == null) {
						oldestUnpaid = due.dueOn();
					}
					if (!due.dueOn().isAfter(today)) {
						headPending = headPending.add(open);
					}
				}
			}
			FeeStatus status = FeeStatus.ON_TIME;
			long daysLate = 0;
			if (headPending.signum() > 0) {
				// The oldest uncovered due is on or before today, because it comes first and something up to today is open.
				daysLate = ChronoUnit.DAYS.between(oldestUnpaid, today);
				status = (daysLate <= graceDays) ? FeeStatus.ON_TIME
						: (daysLate <= defaultedAfterDays) ? FeeStatus.DELAYED : FeeStatus.DEFAULTED;
			}
			heads.put(head, new HeadStatus(head, status, paidTotal, headPending, headRemaining,
					(headPending.signum() > 0) ? oldestUnpaid : null, daysLate));
			if (status.compareTo(worst) > 0) {
				worst = status;
			}
			pendingNow = pendingNow.add(headPending);
			remaining = remaining.add(headRemaining);
		}
		covered.sort(Comparator.comparing(CoveredDue::dueOn).thenComparing(CoveredDue::head));

		LocalDate nextOn = null;
		BigDecimal nextAmount = null;
		for (CoveredDue due : covered) {
			if (due.dueOn().isAfter(today) && due.amount().compareTo(due.covered()) > 0) {
				if (nextOn == null) {
					nextOn = due.dueOn();
					nextAmount = BigDecimal.ZERO;
				}
				if (due.dueOn().equals(nextOn)) {
					nextAmount = nextAmount.add(due.amount().subtract(due.covered()));
				}
			}
		}
		return new Summary(covered, heads, pendingNow, remaining, nextOn, nextAmount, worst);
	}

}
