package com.muhjain.school.model.dto.response;

import com.muhjain.school.util.MonthlyCollectionCalculator.MonthRow;

import java.util.List;

/**
 * Fee collection by month, for the line or bar graph. One row for each month of the school year up to this month.
 * Example: {@code {"month":"2026-04","school":{"due":1000000,"collected":960000,"percent":96.0},"bus":{...}}}
 */
public record MonthlyCollectionResponse(Long sessionId, String sessionName, List<MonthRow> months) {

}
