package com.muhjain.school.fee;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeePlanRepository extends JpaRepository<FeePlan, Long> {

	Optional<FeePlan> findByStudentIdAndSessionId(Long studentId, Long sessionId);

	List<FeePlan> findBySessionId(Long sessionId);

}
