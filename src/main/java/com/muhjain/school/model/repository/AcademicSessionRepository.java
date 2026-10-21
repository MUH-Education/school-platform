package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.AcademicSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {

	/** The session whose dates include {@code today}. At most one, thanks to the no-overlap constraint. */
	Optional<AcademicSession> findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqual(LocalDate today,
			LocalDate sameToday);

	boolean existsByNameIgnoreCase(String name);

	List<AcademicSession> findAllByOrderByStartsOnDesc();

	/** The soonest session that has not started yet. Used to default a new enquiry to "next year". */
	Optional<AcademicSession> findFirstByStartsOnGreaterThanOrderByStartsOnAsc(LocalDate today);

}
