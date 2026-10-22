package com.muhjain.school.util;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.RuleSchedule;
import com.muhjain.school.model.enums.ScheduleType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns the Settings screen's choices into a Spring cron and back (Phase 12; free cron text is out of scope).
 * <ul>
 * <li>DAILY 06:00 ↔ {@code 0 0 6 * * *}</li>
 * <li>WEEKLY MON..SAT 08:30 ↔ {@code 0 30 8 * * MON,TUE,WED,THU,FRI,SAT} (a range like {@code MON-SAT} is read too)</li>
 * <li>MONTHLY 10th 10:00 ↔ {@code 0 0 10 10 * *}</li>
 * </ul>
 */
public final class RuleSchedules {

	private static final List<String> DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

	private RuleSchedules() {
	}

	/** @throws ApiException 400 VALIDATION (field {@code schedule}) for a choice that does not fit its type */
	public static String toCron(RuleSchedule schedule) {
		LocalTime time = schedule.time();
		String start = "0 " + time.getMinute() + " " + time.getHour() + " ";
		return switch (schedule.type()) {
			case DAILY -> {
				if (!schedule.daysOfMonth().isEmpty() || !schedule.daysOfWeek().isEmpty()) {
					throw ApiException.validation("schedule", "every day has no days to pick");
				}
				yield start + "* * *";
			}
			case WEEKLY -> {
				if (schedule.daysOfWeek().isEmpty() || !schedule.daysOfMonth().isEmpty()) {
					throw ApiException.validation("schedule", "every week needs at least one day of the week");
				}
				yield start + "* * " + schedule.daysOfWeek()
					.stream()
					.distinct()
					.sorted()
					.map(d -> DAYS.get(d.getValue() - 1))
					.collect(Collectors.joining(","));
			}
			case MONTHLY -> {
				if (schedule.daysOfMonth().isEmpty() || !schedule.daysOfWeek().isEmpty()) {
					throw ApiException.validation("schedule", "every month needs at least one day of the month");
				}
				if (schedule.daysOfMonth().stream().anyMatch(d -> d == null || d < 1 || d > 28)) {
					throw ApiException.validation("schedule", "days of the month must be 1 to 28");
				}
				yield start + schedule.daysOfMonth()
					.stream()
					.distinct()
					.sorted()
					.map(String::valueOf)
					.collect(Collectors.joining(",")) + " * *";
			}
		};
	}

	/** The choice a cron stands for, or null when it is not one of the three shapes above. */
	public static RuleSchedule fromCron(String cron) {
		if (cron == null) {
			return null;
		}
		String[] p = cron.trim().split("\\s+");
		if (p.length != 6 || !p[0].equals("0") || !p[4].equals("*")) {
			return null;
		}
		try {
			LocalTime time = LocalTime.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]));
			if (p[3].equals("*") && p[5].equals("*")) {
				return new RuleSchedule(ScheduleType.DAILY, time, null, null);
			}
			if (p[3].equals("*")) {
				List<DayOfWeek> days = weekDays(p[5]);
				return (days == null) ? null : new RuleSchedule(ScheduleType.WEEKLY, time, null, days);
			}
			if (p[5].equals("*")) {
				List<Integer> days = new ArrayList<>();
				for (String day : p[3].split(",")) {
					days.add(Integer.parseInt(day));
				}
				return new RuleSchedule(ScheduleType.MONTHLY, time, days, null);
			}
		}
		catch (RuntimeException e) {
			return null;
		}
		return null;
	}

	// "MON,WED" or "MON-SAT" → the days; anything else → null.
	private static List<DayOfWeek> weekDays(String field) {
		List<DayOfWeek> days = new ArrayList<>();
		for (String part : field.split(",")) {
			String[] range = part.split("-");
			int from = DAYS.indexOf(range[0].toUpperCase());
			int to = (range.length == 2) ? DAYS.indexOf(range[1].toUpperCase()) : from;
			if (from < 0 || to < from || range.length > 2) {
				return null;
			}
			for (int i = from; i <= to; i++) {
				days.add(DayOfWeek.of(i + 1));
			}
		}
		return days;
	}

}
