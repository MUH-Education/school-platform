package com.muhjain.school.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.AcademicSession;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {

	Optional<AcademicSession> findByCurrentTrue();

	boolean existsByNameIgnoreCase(String name);

	List<AcademicSession> findAllByOrderByStartsOnDesc();

}
