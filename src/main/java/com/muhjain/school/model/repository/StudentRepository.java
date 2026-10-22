package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

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

	/**
	 * The ACTIVE children born on this month and day, any year, by name. {@code leapDayToo} adds the children born
	 * on 29 February (used on 28 February of a year without a 29th, so they get one wish a year).
	 */
	@Query(value = "select * from student where status = 'ACTIVE' and dob is not null "
			+ "and ((extract(month from dob) = :month and extract(day from dob) = :day) "
			+ "or (:leapDayToo and extract(month from dob) = 2 and extract(day from dob) = 29)) order by name, id",
			nativeQuery = true)
	List<Student> activeBornOn(@Param("month") int month, @Param("day") int day,
			@Param("leapDayToo") boolean leapDayToo);

}
