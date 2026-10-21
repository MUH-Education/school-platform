package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {

	/** The teacher who is class teacher of this class, if there is one. Used for rule 11. */
	Optional<TeacherProfile> findByClassTeacherOf(String classTeacherOf);

	List<TeacherProfile> findByStaffIdIn(List<Long> staffIds);

}
