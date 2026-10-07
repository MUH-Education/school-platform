package com.muhjain.school.student;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransportEnrolmentRepository extends JpaRepository<TransportEnrolment, Long> {

	/** The open row of a child (to_date is null). There is at most one, the database checks it. */
	Optional<TransportEnrolment> findByStudentIdAndToDateIsNull(Long studentId);

	/** A child's bus history, newest first. */
	List<TransportEnrolment> findByStudentIdOrderByFromDateDescIdDesc(Long studentId);

	List<TransportEnrolment> findByStudentId(Long studentId);

	/** The row that covers the day. Example: a child on Route 4 until 30 Nov: 15 Nov → that row, 1 Dec → empty. */
	@Query("select e from TransportEnrolment e where e.studentId = :studentId and e.fromDate <= :day "
			+ "and (e.toDate is null or e.toDate >= :day)")
	Optional<TransportEnrolment> coveringDay(@Param("studentId") Long studentId, @Param("day") LocalDate day);

	/** The last day of the newest closed row. A new row must start after it. Null if the child has no closed row. */
	@Query("select max(e.toDate) from TransportEnrolment e where e.studentId = :studentId")
	LocalDate lastEndDate(@Param("studentId") Long studentId);

	/** Active children on the route on the day. Used for the ROUTE_FULL warning. */
	@Query("select count(distinct e.studentId) from TransportEnrolment e, Student s where s.id = e.studentId "
			+ "and s.status = com.muhjain.school.student.StudentStatus.ACTIVE and e.routeId = :routeId "
			+ "and e.fromDate <= :day and (e.toDate is null or e.toDate >= :day)")
	long countOnRoute(@Param("routeId") Long routeId, @Param("day") LocalDate day);

}
