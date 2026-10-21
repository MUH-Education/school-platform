package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

	boolean existsByHolidayDate(LocalDate holidayDate);

}
