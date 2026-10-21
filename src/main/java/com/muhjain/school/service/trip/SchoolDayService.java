package com.muhjain.school.service.trip;

import com.muhjain.school.model.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Is there school (and so a bus) on a day? Not on a Sunday, not on a day in the {@code holiday} table.
 * Example: Sunday 11 Oct 2026 → no; Tuesday 20 Oct 2026 (Diwali, saved as a holiday) → no.
 */
@Service
public class SchoolDayService {

	private final HolidayRepository holidays;

	public SchoolDayService(HolidayRepository holidays) {
		this.holidays = holidays;
	}

	@Transactional(readOnly = true)
	public boolean isSchoolDay(LocalDate day) {
		return day.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.existsByHolidayDate(day);
	}

}
