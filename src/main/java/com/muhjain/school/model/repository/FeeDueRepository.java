package com.muhjain.school.model.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.FeeDue;

public interface FeeDueRepository extends JpaRepository<FeeDue, Long> {

	List<FeeDue> findByStudentIdAndSessionIdOrderByMonthAsc(Long studentId, Long sessionId);

	Optional<FeeDue> findByStudentIdAndMonth(Long studentId, LocalDate month);

	List<FeeDue> findByStudentIdInAndSessionId(Collection<Long> studentIds, Long sessionId);

}
