package com.muhjain.school.fee;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeePlanRepository extends JpaRepository<FeePlan, Long> {

	Optional<FeePlan> findByStudentIdAndSessionId(Long studentId, Long sessionId);

	/**
	 * The plan, with its row locked until the transaction ends. Payments and plan changes of one child take this
	 * lock first, so two clerks cannot both pass the "not more than unpaid" check at the same moment.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from FeePlan p where p.studentId = :studentId and p.sessionId = :sessionId")
	Optional<FeePlan> lockByStudentIdAndSessionId(@Param("studentId") Long studentId,
			@Param("sessionId") Long sessionId);

	List<FeePlan> findBySessionId(Long sessionId);

}
