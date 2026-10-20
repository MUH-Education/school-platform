package com.muhjain.school.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.muhjain.school.model.enums.FeeHead;
import com.muhjain.school.model.enums.FeeStatus;

/**
 * The fee maths of one child (rule 15 of phase 7). Pure Java: no Spring, no database, no clock. The caller
 * gives "today", {@code defaultedAfterDays}, and one row per month from {@code v_fee_month} (the database
 * already worked out each month's balance, due day and grace day from {@code session_fee_setting}).
 * <p>
 * A month is DUE once today reaches its due date. {@code pendingNow} is the balance of every DUE month;
 * {@code remainingThisYear} is the balance of every month, due or not. The child's status is ON_TIME while
 * today has not passed the oldest unpaid month's grace date, DELAYED until {@code defaultedAfterDays} past its
 * due date, DEFAULTED after that.
 */
public final class FeeStatusCalculator {

	/** One month, as it comes from {@code v_fee_month}. */
	public record MonthInput(Long feeDueId, LocalDate month, BigDecimal schoolAmount, BigDecimal discountAmount,
			BigDecimal busAmount, BigDecimal total, BigDecimal paidSchool, BigDecimal paidBus, BigDecimal paidLate,
			BigDecimal balance, LocalDate dueDate, LocalDate graceDate) {
	}

	/** One month with whether it has come due yet. */
	public record MonthView(Long feeDueId, LocalDate month, BigDecimal total, BigDecimal paid, BigDecimal balance,
			LocalDate dueDate, LocalDate graceDate, boolean due, boolean late) {
	}

	/** The numbers of one fee head. {@code oldestUnpaidDue} and {@code daysLate} are null / 0 when nothing is pending. */
	public record HeadStatus(FeeHead head, FeeStatus status, BigDecimal paid, BigDecimal pendingNow,
			BigDecimal remaining, LocalDate oldestUnpaidDue, long daysLate) {
	}

	/**
	 * Everything for the screen. {@code nextDueOn} is the first day after today with money not yet covered, and
	 * {@code nextDueAmount} the balance of that day's month (null when nothing is coming).
	 */
	public record Summary(List<MonthView> months, List<MonthInput> dues, Map<FeeHead, HeadStatus> heads,
			BigDecimal pendingNow, BigDecimal remainingThisYear, LocalDate nextDueOn, BigDecimal nextDueAmount,
			FeeStatus status) {
	}

	private FeeStatusCalculator() {
	}

	/** @param defaultedAfterDays {@code fees.defaulted_after_days}, example 60 */
	public static Summary calculate(List<MonthInput> rows, LocalDate today, int defaultedAfterDays) {
		List<MonthInput> sorted = rows.stream().sorted(Comparator.comparing(MonthInput::month)).toList();
		List<MonthView> months = new ArrayList<>();
		BigDecimal pendingNow = BigDecimal.ZERO;
		BigDecimal remaining = BigDecimal.ZERO;
		LocalDate oldestUnpaidDue = null;
		LocalDate oldestUnpaidGrace = null;
		LocalDate nextOn = null;
		BigDecimal nextAmount = null;
		Map<FeeHead, BigDecimal> headPaid = new EnumMap<>(FeeHead.class);
		Map<FeeHead, BigDecimal> headPending = new EnumMap<>(FeeHead.class);
		Map<FeeHead, BigDecimal> headRemaining = new EnumMap<>(FeeHead.class);
		Map<FeeHead, LocalDate> headOldestUnpaid = new EnumMap<>(FeeHead.class);
		for (FeeHead head : new FeeHead[] { FeeHead.SCHOOL, FeeHead.BUS }) {
			headPaid.put(head, BigDecimal.ZERO);
			headPending.put(head, BigDecimal.ZERO);
			headRemaining.put(head, BigDecimal.ZERO);
		}
		for (MonthInput row : sorted) {
			boolean due = !row.dueDate().isAfter(today);
			boolean late = today.isAfter(row.graceDate());
			months.add(new MonthView(row.feeDueId(), row.month(), row.total(),
					row.paidSchool().add(row.paidBus()), row.balance(), row.dueDate(), row.graceDate(), due, late));
			remaining = remaining.add(row.balance());
			if (due && row.balance().signum() > 0) {
				pendingNow = pendingNow.add(row.balance());
				if (oldestUnpaidDue == null) {
					oldestUnpaidDue = row.dueDate();
					oldestUnpaidGrace = row.graceDate();
				}
			}
			else if (!due && row.balance().signum() > 0) {
				if (nextOn == null) {
					nextOn = row.dueDate();
					nextAmount = BigDecimal.ZERO;
				}
				if (row.dueDate().equals(nextOn)) {
					nextAmount = nextAmount.add(row.balance());
				}
			}
			BigDecimal schoolOpen = row.schoolAmount().subtract(row.discountAmount()).subtract(row.paidSchool());
			BigDecimal busOpen = row.busAmount().subtract(row.paidBus());
			headPaid.merge(FeeHead.SCHOOL, row.paidSchool(), BigDecimal::add);
			headPaid.merge(FeeHead.BUS, row.paidBus(), BigDecimal::add);
			if (schoolOpen.signum() > 0) {
				headRemaining.merge(FeeHead.SCHOOL, schoolOpen, BigDecimal::add);
				headOldestUnpaid.putIfAbsent(FeeHead.SCHOOL, row.dueDate());
				if (due) {
					headPending.merge(FeeHead.SCHOOL, schoolOpen, BigDecimal::add);
				}
			}
			if (busOpen.signum() > 0) {
				headRemaining.merge(FeeHead.BUS, busOpen, BigDecimal::add);
				headOldestUnpaid.putIfAbsent(FeeHead.BUS, row.dueDate());
				if (due) {
					headPending.merge(FeeHead.BUS, busOpen, BigDecimal::add);
				}
			}
		}
		FeeStatus status = FeeStatus.ON_TIME;
		if (oldestUnpaidDue != null) {
			status = statusOf(today, oldestUnpaidGrace, oldestUnpaidDue, defaultedAfterDays);
		}
		Map<FeeHead, HeadStatus> heads = new EnumMap<>(FeeHead.class);
		for (FeeHead head : new FeeHead[] { FeeHead.SCHOOL, FeeHead.BUS }) {
			LocalDate oldest = headOldestUnpaid.get(head);
			boolean pending = headPending.get(head).signum() > 0;
			long daysLate = (pending && oldest != null) ? ChronoUnit.DAYS.between(oldest, today) : 0;
			FeeStatus headStatus = (!pending || oldest == null) ? FeeStatus.ON_TIME
					: statusOf(today, graceOf(sorted, oldest), oldest, defaultedAfterDays);
			heads.put(head, new HeadStatus(head, headStatus, headPaid.get(head), headPending.get(head),
					headRemaining.get(head), pending ? oldest : null, daysLate));
		}
		return new Summary(months, sorted, heads, pendingNow, remaining, nextOn, nextAmount, status);
	}

	private static LocalDate graceOf(List<MonthInput> rows, LocalDate dueDate) {
		return rows.stream().filter(r -> r.dueDate().equals(dueDate)).map(MonthInput::graceDate).findFirst()
				.orElse(dueDate);
	}

	private static FeeStatus statusOf(LocalDate today, LocalDate graceDate, LocalDate dueDate, int defaultedAfterDays) {
		if (!today.isAfter(graceDate)) {
			return FeeStatus.ON_TIME;
		}
		long daysLate = ChronoUnit.DAYS.between(dueDate, today);
		return (daysLate <= defaultedAfterDays) ? FeeStatus.DELAYED : FeeStatus.DEFAULTED;
	}

}
