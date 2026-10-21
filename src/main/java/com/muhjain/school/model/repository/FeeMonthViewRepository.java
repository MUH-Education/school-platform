package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.FeeMonthView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FeeMonthViewRepository extends JpaRepository<FeeMonthView, Long> {

	List<FeeMonthView> findByStudentIdAndSessionIdOrderByMonthAsc(Long studentId, Long sessionId);

	List<FeeMonthView> findByStudentIdInAndSessionId(Collection<Long> studentIds, Long sessionId);

}
