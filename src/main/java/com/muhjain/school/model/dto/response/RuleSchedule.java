package com.muhjain.school.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.muhjain.school.model.enums.ScheduleType;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * When a CRON rule goes, as the Settings screen shows it. The server builds the cron from it
 * ({@code util.RuleSchedules}). School time.
 * Examples: {@code {"type": "MONTHLY", "time": "10:00", "daysOfMonth": [10]}} · {@code {"type": "WEEKLY", "time":
 * "08:30", "daysOfWeek": ["MONDAY", ..., "SATURDAY"]}} · {@code {"type": "DAILY", "time": "06:00"}}.
 *
 * @param daysOfMonth MONTHLY only: 1 to 28 (so it happens every month, February too)
 * @param daysOfWeek WEEKLY only: at least one day
 */
public record RuleSchedule(@NotNull ScheduleType type, @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
		List<Integer> daysOfMonth, List<DayOfWeek> daysOfWeek) {

	public RuleSchedule {
		daysOfMonth = (daysOfMonth == null) ? List.of() : List.copyOf(daysOfMonth);
		daysOfWeek = (daysOfWeek == null) ? List.of() : List.copyOf(daysOfWeek);
	}

}
