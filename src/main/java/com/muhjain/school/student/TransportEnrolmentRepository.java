package com.muhjain.school.student;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportEnrolmentRepository extends JpaRepository<TransportEnrolment, Long> {

	/** The open row of a child (to_date is null). There is at most one, the database checks it. */
	Optional<TransportEnrolment> findByStudentIdAndToDateIsNull(Long studentId);

	/** A child's bus history, newest first. */
	List<TransportEnrolment> findByStudentIdOrderByFromDateDescIdDesc(Long studentId);

	List<TransportEnrolment> findByStudentId(Long studentId);

}
