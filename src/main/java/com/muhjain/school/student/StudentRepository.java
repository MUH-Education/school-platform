package com.muhjain.school.student;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

	List<Student> findByIdIn(Collection<Long> ids);

}
