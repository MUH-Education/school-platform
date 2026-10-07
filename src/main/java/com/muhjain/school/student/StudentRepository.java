package com.muhjain.school.student;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

	List<Student> findByIdIn(Collection<Long> ids);

	/**
	 * Is this child saved already? Same name (capital letters do not matter), same date of birth, and one of the
	 * child's phones is {@code phone}. Used by the import, so a file that is uploaded twice does not make twins.
	 */
	@Query("select count(s) > 0 from Student s where lower(s.name) = :name and s.dob = :dob and exists "
			+ "(select 1 from StudentGuardian sg, Guardian g where sg.studentId = s.id and g.id = sg.guardianId "
			+ "and g.phone = :phone)")
	boolean existsSameChild(@Param("name") String lowerName, @Param("dob") LocalDate dob,
			@Param("phone") String phone);

}
