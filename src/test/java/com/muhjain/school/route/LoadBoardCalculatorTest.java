package com.muhjain.school.route;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule 16 of docs/phases/phase-2-vehicles-staff-routes.md. No Spring, no database.
 * Settings in every example: 11 months, bus fee 8800 a year, 95 percent collected.
 * Money is compared with {@code isEqualTo(new BigDecimal("..."))}, so the number of decimals counts too.
 */
class LoadBoardCalculatorTest {

	private static final int MONTHS = 11;

	private static final BigDecimal FEE = new BigDecimal("8800");

	private static final BigDecimal PCT = new BigDecimal("95");

	private static final BigDecimal VAN_COST = new BigDecimal("30300.00");

	private static LoadBoardCalculator.RouteLoad calc(Integer seats, BigDecimal monthlyCost, int children) {
		return LoadBoardCalculator.route(seats, monthlyCost, children, MONTHS, FEE, PCT);
	}

	private static BigDecimal money(String text) {
		return new BigDecimal(text);
	}

	@Test
	void route4ExampleFromTheDocs() {
		// Route 4: 19 children, Van 4 with 14 seats, 30,300 a month.
		LoadBoardCalculator.RouteLoad row = calc(14, VAN_COST, 19);

		assertThat(row.seats()).isEqualTo(14);
		assertThat(row.children()).isEqualTo(19);
		assertThat(row.load()).isEqualTo(money("1.36"));
		assertThat(row.overBy()).isEqualTo(5);
		assertThat(row.spare()).isZero();
		assertThat(row.yearlyCost()).isEqualTo(money("333300.00"));
		assertThat(row.costPerChild()).isEqualTo(money("17542.11"));
		assertThat(row.feeGot()).isEqualTo(money("158840.00"));
		assertThat(row.surplus()).isEqualTo(money("-174460.00"));
		assertThat(row.verdict()).isEqualTo(LoadBoardCalculator.Verdict.OVER);
	}

	@Test
	void routeWithNoVehicle() {
		LoadBoardCalculator.RouteLoad row = calc(null, null, 6);

		assertThat(row.verdict()).isEqualTo(LoadBoardCalculator.Verdict.NO_VEHICLE);
		assertThat(row.seats()).isNull();
		assertThat(row.load()).isNull();
		assertThat(row.yearlyCost()).isNull();
		assertThat(row.costPerChild()).isNull();
		assertThat(row.surplus()).isNull();
		assertThat(row.overBy()).isZero();
		assertThat(row.spare()).isZero();
		// The children still pay their fee: 6 x 8800 x 95 / 100.
		assertThat(row.children()).isEqualTo(6);
		assertThat(row.feeGot()).isEqualTo(money("50160.00"));
	}

	@Test
	void routeWithNoChildren() {
		LoadBoardCalculator.RouteLoad row = calc(14, VAN_COST, 0);

		assertThat(row.verdict()).isEqualTo(LoadBoardCalculator.Verdict.NO_CHILDREN);
		assertThat(row.load()).isEqualTo(money("0.00"));
		assertThat(row.spare()).isEqualTo(14);
		assertThat(row.overBy()).isZero();
		assertThat(row.yearlyCost()).isEqualTo(money("333300.00"));
		assertThat(row.costPerChild()).isNull();
		assertThat(row.feeGot()).isEqualTo(money("0.00"));
		assertThat(row.surplus()).isEqualTo(money("-333300.00"));
	}

	@Test
	void thinRoute() {
		// 6 children on 14 seats: load 0.43. Below 0.6, so THIN.
		LoadBoardCalculator.RouteLoad row = calc(14, VAN_COST, 6);

		assertThat(row.verdict()).isEqualTo(LoadBoardCalculator.Verdict.THIN);
		assertThat(row.load()).isEqualTo(money("0.43"));
		assertThat(row.spare()).isEqualTo(8);
		assertThat(row.overBy()).isZero();
		assertThat(row.costPerChild()).isEqualTo(money("55550.00"));
		assertThat(row.feeGot()).isEqualTo(money("50160.00"));
		assertThat(row.surplus()).isEqualTo(money("-283140.00"));
	}

	@Test
	void loadOf06IsOkAndBelowIsThin() {
		// 6 of 10 seats is exactly 0.6: OK. 5 of 10 is 0.5: THIN.
		assertThat(calc(10, VAN_COST, 6).verdict()).isEqualTo(LoadBoardCalculator.Verdict.OK);
		assertThat(calc(10, VAN_COST, 5).verdict()).isEqualTo(LoadBoardCalculator.Verdict.THIN);
	}

	@Test
	void verdictUsesTheTrueLoadNotTheRoundedOne() {
		// 119 of 200 is 0.595. It shows as 0.60 (rounded) but it is below 0.6, so THIN.
		LoadBoardCalculator.RouteLoad row = calc(200, VAN_COST, 119);

		assertThat(row.load()).isEqualTo(money("0.60"));
		assertThat(row.verdict()).isEqualTo(LoadBoardCalculator.Verdict.THIN);
	}

	@Test
	void fullVehicleIsOkAndOneMoreChildIsOver() {
		LoadBoardCalculator.RouteLoad full = calc(14, VAN_COST, 14);
		assertThat(full.verdict()).isEqualTo(LoadBoardCalculator.Verdict.OK);
		assertThat(full.load()).isEqualTo(money("1.00"));
		assertThat(full.overBy()).isZero();
		assertThat(full.spare()).isZero();

		LoadBoardCalculator.RouteLoad over = calc(14, VAN_COST, 15);
		assertThat(over.verdict()).isEqualTo(LoadBoardCalculator.Verdict.OVER);
		assertThat(over.overBy()).isEqualTo(1);
	}

	@Test
	void healthyRoute() {
		// 12 children on 14 seats: load 0.86.
		LoadBoardCalculator.RouteLoad row = calc(14, VAN_COST, 12);

		assertThat(row.verdict()).isEqualTo(LoadBoardCalculator.Verdict.OK);
		assertThat(row.load()).isEqualTo(money("0.86"));
		assertThat(row.spare()).isEqualTo(2);
		assertThat(row.costPerChild()).isEqualTo(money("27775.00"));
	}

	@Test
	void settingsChangeTheMoney() {
		// 10 months, fee 9000, 100 percent: yearly cost 303,000. Fee got 10 x 9000 = 90,000.
		LoadBoardCalculator.RouteLoad row = LoadBoardCalculator.route(14, VAN_COST, 10, 10, new BigDecimal("9000"),
				new BigDecimal("100"));

		assertThat(row.yearlyCost()).isEqualTo(money("303000.00"));
		assertThat(row.feeGot()).isEqualTo(money("90000.00"));
		assertThat(row.surplus()).isEqualTo(money("-213000.00"));
		// 0 percent collected: no fee at all.
		assertThat(LoadBoardCalculator.route(14, VAN_COST, 10, 10, new BigDecimal("9000"), BigDecimal.ZERO).feeGot())
			.isEqualTo(money("0.00"));
	}

	@Test
	void totalsOfTheWholeFleet() {
		// Fleet: 3 vehicles (14 + 14 + 26 seats), the third has no route. Cost 30,300 a month each.
		LoadBoardCalculator.RouteLoad route4 = calc(14, VAN_COST, 19);
		LoadBoardCalculator.RouteLoad route5 = calc(14, VAN_COST, 6);
		List<LoadBoardCalculator.RouteLoad> rows = List.of(route4, route5);

		LoadBoardCalculator.Totals totals = LoadBoardCalculator.totals(rows, 3, 54, new BigDecimal("999900.00"));

		assertThat(totals.routes()).isEqualTo(2);
		assertThat(totals.vehicles()).isEqualTo(3);
		assertThat(totals.seats()).isEqualTo(54);
		assertThat(totals.children()).isEqualTo(25);
		assertThat(totals.load()).isEqualTo(money("0.46"));
		assertThat(totals.yearlyCost()).isEqualTo(money("999900.00"));
		assertThat(totals.costPerChild()).isEqualTo(money("39996.00"));
		assertThat(totals.feeGot()).isEqualTo(money("209000.00"));
		assertThat(totals.surplus()).isEqualTo(money("-790900.00"));
	}

	@Test
	void totalsWithNoChildrenAndNoSeats() {
		LoadBoardCalculator.Totals none = LoadBoardCalculator.totals(List.of(), 0, 0, BigDecimal.ZERO);

		assertThat(none.routes()).isZero();
		assertThat(none.load()).isNull();
		assertThat(none.costPerChild()).isNull();
		assertThat(none.yearlyCost()).isEqualTo(money("0.00"));
		assertThat(none.feeGot()).isEqualTo(money("0.00"));
		assertThat(none.surplus()).isEqualTo(money("0.00"));

		LoadBoardCalculator.Totals empty = LoadBoardCalculator.totals(List.of(calc(14, VAN_COST, 0)), 1, 14,
				new BigDecimal("333300.00"));
		assertThat(empty.costPerChild()).isNull();
		assertThat(empty.load()).isEqualTo(money("0.00"));
	}

}
