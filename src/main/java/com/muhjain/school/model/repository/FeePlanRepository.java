package com.muhjain.school.model.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.muhjain.school.model.entity.FeePlan;

public interface FeePlanRepository extends JpaRepository<FeePlan, Long> {

	/** Every time-sliced row of a child's plan in a session, oldest first. */
	List<FeePlan> findByStudentIdAndSessionIdOrderByFromMonthAsc(Long studentId, Long sessionId);

	/** The newest slice: what the plan is right now. */
	Optional<FeePlan> findFirstByStudentIdAndSessionIdOrderByFromMonthDesc(Long studentId, Long sessionId);

	/**
	 * The newest slice, with its row locked until the transaction ends. Payments and plan changes of one child
	 * take this lock first, so two clerks cannot both pass the "not more than unpaid" check at the same moment.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from FeePlan p where p.studentId = :studentId and p.sessionId = :sessionId "
			+ "order by p.fromMonth desc limit 1")
	Optional<FeePlan> lockNewestByStudentIdAndSessionId(@Param("studentId") Long studentId,
			@Param("sessionId") Long sessionId);

	List<FeePlan> findBySessionId(Long sessionId);

	List<FeePlan> findBySessionIdAndStudentIdIn(Long sessionId, java.util.Collection<Long> studentIds);

}
