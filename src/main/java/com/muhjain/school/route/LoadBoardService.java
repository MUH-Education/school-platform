package com.muhjain.school.route;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.muhjain.school.setting.SettingService;
import com.muhjain.school.vehicle.VehicleService;
import com.muhjain.school.vehicle.VehicleSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Routes and load screen (rule 16). This class only collects the numbers. The maths is in
 * {@link LoadBoardCalculator}.
 * Settings used: {@code transport.months_operated}, {@code transport.bus_fee_per_year},
 * {@code transport.collection_pct}.
 */
@Service
public class LoadBoardService {

	private final RouteRepository routes;

	private final VehicleService vehicleService;

	private final StudentCounts studentCounts;

	private final SettingService settings;

	private final Clock clock;

	public LoadBoardService(RouteRepository routes, VehicleService vehicleService, StudentCounts studentCounts,
			SettingService settings, Clock clock) {
		this.routes = routes;
		this.vehicleService = vehicleService;
		this.studentCounts = studentCounts;
		this.settings = settings;
		this.clock = clock;
	}

	/** Every active route with its numbers for today, and the totals of the whole fleet. */
	@Transactional(readOnly = true)
	public LoadBoardResponse board() {
		LocalDate today = LocalDate.now(clock);
		int months = Integer.parseInt(settings.value("transport.months_operated"));
		BigDecimal fee = new BigDecimal(settings.value("transport.bus_fee_per_year"));
		BigDecimal pct = new BigDecimal(settings.value("transport.collection_pct"));

		List<Route> active = routes.findByActiveTrueOrderByIdAsc();
		Map<Long, VehicleSummary> vehicles = vehicleService
			.summaries(active.stream().map(Route::getVehicleId).filter(Objects::nonNull).toList());

		List<LoadBoardRow> rows = new ArrayList<>();
		List<LoadBoardCalculator.RouteLoad> loads = new ArrayList<>();
		for (Route route : active) {
			VehicleSummary vehicle = vehicles.get(route.getVehicleId());
			int children = studentCounts.childrenOnRoute(route.getId(), today);
			LoadBoardCalculator.RouteLoad load = LoadBoardCalculator.route((vehicle != null) ? vehicle.seats() : null,
					(vehicle != null) ? vehicle.monthlyCost() : null, children, months, fee, pct);
			loads.add(load);
			rows.add(LoadBoardRow.of(route, vehicle, load));
		}

		// The fleet: every vehicle that is turned on, also one that runs no route. It still costs money.
		List<VehicleSummary> fleet = vehicleService.activeSummaries();
		int fleetSeats = fleet.stream().mapToInt(VehicleSummary::seats).sum();
		BigDecimal fleetCost = fleet.stream()
			.map(v -> LoadBoardCalculator.yearlyCost(v.monthlyCost(), months))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new LoadBoardResponse(rows,
				LoadBoardCalculator.totals(loads, fleet.size(), fleetSeats, fleetCost));
	}

}
