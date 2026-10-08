package com.muhjain.school.analytics;

import java.util.List;

import com.muhjain.school.analytics.MonthlyCollectionCalculator.MonthRow;

/**
 * Fee collection by month, for the line or bar graph. One row for each month of the school year up to this month.
 * Example: {@code {"month":"2026-04","school":{"due":1000000,"collected":960000,"percent":96.0},"bus":{...}}}
 */
public record MonthlyCollectionResponse(Long sessionId, String sessionName, List<MonthRow> months) {

}
