package com.muhjain.school.staff;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {

	/** The teacher who is class teacher of this class, if there is one. Used for rule 11. */
	Optional<TeacherProfile> findByClassTeacherOf(String classTeacherOf);

	List<TeacherProfile> findByStaffIdIn(List<Long> staffIds);

}
