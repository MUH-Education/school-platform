package com.muhjain.school.student;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, Long> {

	/** The phones of one child, oldest link first. */
	List<StudentGuardian> findByStudentIdOrderByIdAsc(Long studentId);

	List<StudentGuardian> findByStudentIdInOrderByIdAsc(Collection<Long> studentIds);

	Optional<StudentGuardian> findByStudentIdAndGuardianId(Long studentId, Long guardianId);

	/** How many children use this phone. Example: Aryan and Siya share one → 2. */
	long countByGuardianId(Long guardianId);

	List<StudentGuardian> findByGuardianId(Long guardianId);

}
