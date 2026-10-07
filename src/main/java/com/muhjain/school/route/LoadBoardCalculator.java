package com.muhjain.school.route;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The maths of the "Routes and load" screen (rule 16 and 17). Pure Java: no Spring, no database, no clock.
 * Money is {@link BigDecimal}, never double. Money and load are rounded to 2 decimals, half up.
 * <p>
 * Example: Route 4, 19 children, Van 4 with 14 seats and 30,300 a month, 11 months, fee 8,800, 95 percent collected.
 * load 1.36, over by 5, yearly cost 333,300.00, cost per child 17,542.11, fee got 158,840.00,
 * surplus -174,460.00, verdict OVER.
 */
public final class LoadBoardCalculator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private static final int MONEY_SCALE = 2;

	private LoadBoardCalculator() {
	}

	/** What the screen says about one route. */
	public enum Verdict {

		/** The route has no vehicle. */
		NO_VEHICLE,
		/** The route has a vehicle and no children. */
		NO_CHILDREN,
		/** More children than seats. */
		OVER,
		/** Load below 0.6: the vehicle is mostly empty. */
		THIN,
		OK

	}

	/**
	 * One route.
	 *
	 * @param seats seats of the vehicle, null if no vehicle
	 * @param load children ÷ seats, null if no vehicle
	 * @param overBy children above the seats, else 0
	 * @param spare seats nobody uses, else 0
	 * @param yearlyCost vehicle monthly cost × months operated, null if no vehicle
	 * @param costPerChild yearly cost ÷ children, null if no vehicle or no children
	 * @param feeGot children × bus fee per year × collection percent ÷ 100
	 * @param surplus fee got − yearly cost, null if no vehicle
	 */
	public record RouteLoad(Integer seats, int children, BigDecimal load, int overBy, int spare,
			BigDecimal yearlyCost, BigDecimal costPerChild, BigDecimal feeGot, BigDecimal surplus,
			Verdict verdict) {

	}

	/**
	 * The whole fleet.
	 *
	 * @param routes number of routes in the table
	 * @param vehicles vehicles that are turned on (also those without a route)
	 * @param seats seats of those vehicles
	 * @param children children on all routes
	 * @param load children ÷ seats, null if no seats
	 * @param yearlyCost yearly cost of those vehicles
	 * @param costPerChild yearly cost ÷ children, null if no children
	 * @param feeGot fee got on all routes
	 * @param surplus fee got − yearly cost
	 */
	public record Totals(int routes, int vehicles, int seats, int children, BigDecimal load, BigDecimal yearlyCost,
			BigDecimal costPerChild, BigDecimal feeGot, BigDecimal surplus) {

	}

	/**
	 * @param seats seats of the vehicle, or null if the route has no vehicle
	 * @param monthlyCost monthly cost of the vehicle, or null if no vehicle
	 * @param children children on the route today
	 * @param monthsOperated setting {@code transport.months_operated}, example 11
	 * @param busFeePerYear setting {@code transport.bus_fee_per_year}, example 8800
	 * @param collectionPct setting {@code transport.collection_pct}, example 95
	 */
	public static RouteLoad route(Integer seats, BigDecimal monthlyCost, int children, int monthsOperated,
			BigDecimal busFeePerYear, BigDecimal collectionPct) {
		BigDecimal feeGot = feeGot(children, busFeePerYear, collectionPct);
		if (seats == null) {
			return new RouteLoad(null, children, null, 0, 0, null, null, feeGot, null, Verdict.NO_VEHICLE);
		}
		BigDecimal yearlyCost = yearlyCost(monthlyCost, monthsOperated);
		return new RouteLoad(seats, children, ratio(children, seats), Math.max(0, children - seats),
				Math.max(0, seats - children), yearlyCost, perChild(yearlyCost, children), feeGot,
				feeGot.subtract(yearlyCost), verdict(seats, children));
	}

	/**
	 * @param rows the rows of the table
	 * @param vehicles vehicles that are turned on
	 * @param seats seats of those vehicles
	 * @param fleetYearlyCost yearly cost of those vehicles (from {@link #yearlyCost})
	 */
	public static Totals totals(List<RouteLoad> rows, int vehicles, int seats, BigDecimal fleetYearlyCost) {
		int children = rows.stream().mapToInt(RouteLoad::children).sum();
		BigDecimal feeGot = rows.stream()
			.map(RouteLoad::feeGot)
			.reduce(BigDecimal.ZERO, BigDecimal::add)
			.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		BigDecimal yearlyCost = fleetYearlyCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		return new Totals(rows.size(), vehicles, seats, children, (seats > 0) ? ratio(children, seats) : null,
				yearlyCost, perChild(yearlyCost, children), feeGot, feeGot.subtract(yearlyCost));
	}

	/** Monthly cost × months. Example: 30,300 × 11 = 333,300.00. Also used for the whole fleet. */
	public static BigDecimal yearlyCost(BigDecimal monthlyCost, int monthsOperated) {
		return monthlyCost.multiply(BigDecimal.valueOf(monthsOperated)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	private static BigDecimal feeGot(int children, BigDecimal busFeePerYear, BigDecimal collectionPct) {
		return BigDecimal.valueOf(children)
			.multiply(busFeePerYear)
			.multiply(collectionPct)
			.divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
	}

	private static BigDecimal perChild(BigDecimal yearlyCost, int children) {
		return (children > 0) ? yearlyCost.divide(BigDecimal.valueOf(children), MONEY_SCALE, RoundingMode.HALF_UP)
				: null;
	}

	private static BigDecimal ratio(int children, int seats) {
		return BigDecimal.valueOf(children).divide(BigDecimal.valueOf(seats), 2, RoundingMode.HALF_UP);
	}

	// Rule 16, in this order. THIN is "load below 0.6", worked with whole numbers so rounding cannot change it:
	// children / seats < 3 / 5  is the same as  children x 5 < seats x 3.
	private static Verdict verdict(int seats, int children) {
		if (children == 0) {
			return Verdict.NO_CHILDREN;
		}
		if (children > seats) {
			return Verdict.OVER;
		}
		if (children * 5L < seats * 3L) {
			return Verdict.THIN;
		}
		return Verdict.OK;
	}

}
