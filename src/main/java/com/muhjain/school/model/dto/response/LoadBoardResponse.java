package com.muhjain.school.model.dto.response;

import java.util.List;
import com.muhjain.school.model.entity.Route;
import com.muhjain.school.util.LoadBoardCalculator;

/**
 * The Routes and load screen: one row per active route, and the totals of the whole fleet.
 * Example: {@code { "routes": [ {...Route 1...}, ... ], "totals": { "routes": 9, "vehicles": 9, "seats": 150,
 * "children": 0, "load": 0.00, "yearlyCost": 2999700.00, "costPerChild": null, "feeGot": 0.00,
 * "surplus": -2999700.00 } }}
 */
public record LoadBoardResponse(List<LoadBoardRow> routes, LoadBoardCalculator.Totals totals) {

}
